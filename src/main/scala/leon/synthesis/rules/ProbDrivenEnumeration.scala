/* Copyright 2009-2016 EPFL, Lausanne */

package leon
package synthesis
package rules

import evaluators._
import leon.grammars.enumerators.CandidateScorer.MeetsSpec
import leon.grammars.{Expansion, ExpansionExpr, Label}
import leon.grammars.enumerators._
import purescala.Expressions._
import purescala.Constructors._
import purescala.ExprOps._
import purescala.DefOps._
import purescala.Common.Identifier
import purescala.Definitions.Program
import utils.MutableExpr
import solvers._

abstract class ProbDrivenEnumerationLike(name: String) extends Rule(name){

  def rootLabel(p: Problem, sctx: SynthesisContext): Label

  private case object UnsatPCException extends Exception("Unsat. PC")

  class NonDeterministicProgram(
    outerCtx: SearchContext,
    outerP: Problem,
    optimize: Boolean
  ) {

    import outerCtx.reporter._

    val solverTo = 10000

    // Create a fresh solution function with the best solution around the
    // current STE as body
    val fd = {
      val fd = outerCtx.functionContext.duplicate()

      fd.fullBody = postMap {
        case src if src eq outerCtx.source =>
          val body = new PartialSolution(outerCtx.search.strat, true)(outerCtx)
            .solutionAround(outerCtx.currentNode)(MutableExpr(NoTree(outerP.outType)))
            .getOrElse(fatalError("Unable to get outer solution"))
            .term

          Some(body)
        case _ =>
          None
      }(fd.fullBody)

      fd
    }

    val outerExamples = {
      val fromProblem = outerP.qebFiltered(outerCtx).eb.examples
      val inOut = fromProblem.filter(_.isInstanceOf[InOutExample])
      // We are forced to take all in-out examples
      if (inOut.nonEmpty) inOut
      // otherwise we prefer one example
      else if (fromProblem.nonEmpty) fromProblem.take(1)
      else {
        // If we have none, generate one with the solver
        val solverF = SolverFactory.getFromSettings(outerCtx, outerCtx.program).withTimeout(solverTo)
        val solver  = solverF.getNewSolver()
        try {
          solver.assertCnstr(outerP.pc.toClause)
          solver.check match {
            case Some(true) =>
              val model = solver.getModel
              Seq(InExample(outerP.as map (id => model.getOrElse(id, simplestValue(id.getType)))))
            case None =>
              warning("Could not solve path condition")
              Seq(InExample(outerP.as.map(_.getType) map simplestValue))
            case Some(false) =>
              warning("PC is not satisfiable.")
              throw UnsatPCException
          }
        } finally {
          solverF.reclaim(solver)
        }
      }
    }

    // Create a program replacing the synthesis source by the fresh solution
    // function
    val (outerToInner, innerToOuter, solutionBox, program) = {
      val t = funDefReplacer {
        case fd2 if fd2 == outerCtx.functionContext =>
          Some(fd)

        case fd2 =>
          Some(fd2.duplicate())
      }

      val innerProgram = transformProgram(t, outerCtx.program)

      val solutionBox = collect[MutableExpr] {
        case me: MutableExpr => Set(me)
        case _ => Set()
      }(fd.fullBody).head

      (t, t.inverse, solutionBox, innerProgram)
    }

    // It should be set to the solution you want to check at each time.
    // Usually it will either be cExpr or a concrete solution.
    private def setSolution(e: Expr) = solutionBox.underlying = e

    implicit val sctx = new SynthesisContext(outerCtx, outerCtx.settings, fd, program)

    val p = {
      implicit val bindings = Map[Identifier, Identifier]()

      Problem(
        as = outerP.as.map(outerToInner.transform),
        ws = outerToInner.transform(outerP.ws),
        pc = outerP.pc.map(outerToInner.transform),
        phi = outerToInner.transform(outerP.phi),
        xs = outerP.xs.map(outerToInner.transform),
        eb = ExamplesBank(outerExamples map (_.map(outerToInner.transform(_)(Map.empty))), Seq())
      )
    }

    var examples = p.eb.examples

    private val spec = letTuple(p.xs, solutionBox, p.phi)

    val useOptTimeout = sctx.findOptionOrDefault(SynthesisPhase.optUntrusted)
    // Limit prob. programs
    val (minLogProb, maxEnumerated) = {
      import SynthesisPhase._
      if (sctx.findOptionOrDefault(optMode) == Modes.Probwise)
        (-1000000000.0, 10000000) // Run forever in probwise-only mode
      else
        (-80.0, 10000)
    }

    val fullEvaluator = new TableEvaluator(sctx, program)
    val partialEvaluator = new PartialExpansionEvaluator(sctx, program)
    val solverF    = SolverFactory.getFromSettings(sctx, program).withTimeout(solverTo)
    val topLabel   = rootLabel(p, sctx)
    val grammar    = grammars.default(sctx, p)

    debug("Examples:\n" + examples.map(_.asString).mkString("\n"))
    val timers     = sctx.timers.synthesis.applications.get("Prob-Enum")

    // Evaluates a candidate against an example in the correct environment
    def evalCandidate(expr: Expr, evalr: Evaluator)(ex: Example): evalr.EvaluationResult = timers.eval.timed {
      setSolution(expr)

      def withBindings(e: Expr) = p.pc.bindings.foldRight(e) {
        case ((id, v), bd) => let(id, v, bd)
      }

      val testExpr = ex match {
        case InExample(_) =>
          spec
        case InOutExample(_, outs) =>
          equality(expr, tupleWrap(outs))
      }

      evalr.eval(withBindings(testExpr), p.as.zip(ex.ins).toMap)
    }

    // Tests a candidate solution against an example in the correct environment
    def testCandidate(expr: Expr)(ex: Example): Option[Boolean] = {
      evalCandidate(expr, fullEvaluator)(ex) match {
        case EvaluationResults.Successful(value) =>
          Some(value == BooleanLiteral(true))
        case EvaluationResults.RuntimeError(err) =>
          debug(s"RE testing CE: $err")
          debug(s"  Failing example: $ex")
          debug(s"  Rejecting $expr")
          Some(false)
        case EvaluationResults.EvaluatorError(err) =>
          debug(s"Error testing CE: $err")
          debug(s"  Removing $ex")
          examples = examples diff Seq(ex)
          None
      }
    }

    private class NoRecEvaluator(sctx: SynthesisContext, pgm: Program) extends TableEvaluator(sctx, pgm) {
      override def e(expr: Expr)(implicit rctx: RC, gctx: GC): Expr = expr match {
        case MutableExpr(_) =>
          throw new EvalError("Trying to normalize based on rec. call body")
        case other => super.e(other)
      }
    }

    private val noRecEvaluator = new NoRecEvaluator(sctx, program)

    // Do not set the solution to expr
    def rawEvalCandidate(expr: Expr, ex: Example): EvaluationResults.Result[Expr] = {
      def withBindings(e: Expr) = p.pc.bindings.foldRight(e) {
        case ((id, v), bd) => let(id, v, bd)
      }

      val res = noRecEvaluator.eval(withBindings(expr), p.as.zip(ex.ins).toMap)
      // res match {
      //   case EvaluationResults.Successful(value) =>
      //   case EvaluationResults.RuntimeError(err) =>
      //     debug(s"RE testing CE: $err")
      //     debug(s"  Failing example: $ex")
      //   case EvaluationResults.EvaluatorError(err) =>
      //     debug(s"Error testing CE: $err")
      // }

      res
    }

    def partialTestCandidate(expansion: Expansion[Label, Expr], ex: Example): MeetsSpec.MeetsSpec = {
      val expr = ExpansionExpr(expansion)
      val res = evalCandidate(expr, partialEvaluator)(ex)
      res match {
        case EvaluationResults.Successful(BooleanLiteral(true)) => MeetsSpec.YES
        case EvaluationResults.Successful(BooleanLiteral(false)) => MeetsSpec.NO
        case EvaluationResults.Successful(_) => MeetsSpec.NOTYET
        case EvaluationResults.RuntimeError(err) => MeetsSpec.NO
        case EvaluationResults.EvaluatorError(err) => MeetsSpec.NOTYET
      }
    }

    /*val restartable = enum == "eqclasses" || enum == "topdown-opt"

    def mkEnum = (enum match {
      case "eqclasses" =>
        ??? // This is disabled currently
        new EqClassesEnumerator(grammar, topLabel, p.as, examples, program)
      case "bottomup"  =>
        ??? // This is disabled currently
        new ProbwiseBottomupEnumerator(grammar, topLabel)
      case _ =>
        val disambiguate = enum match {
          case "topdown" => false
          case "topdown-opt" => true
          case _ =>
            warning(s"Enumerator $enum not recognized, falling back to top-down...")
            false
        }
        val scorer = new CandidateScorer[Label, Expr](partialTestCandidate, _ => examples)
        new ProbwiseTopdownEnumerator(grammar, topLabel, scorer, examples, rawEvalCandidate(_, _).result, maxGen, maxValidated, disambiguate)
    }).iterator(topLabel)*/


    val restartable = optimize

    def mkEnum = {
      val scorer = new CandidateScorer[Label, Expr](partialTestCandidate, _ => examples)
      new ProbwiseTopdownEnumerator(grammar, topLabel, scorer, examples, rawEvalCandidate, minLogProb, maxEnumerated, optimize)
    }.iterator(topLabel)


    var it = mkEnum
    debug("Grammar:")
    debug(grammar.asString)

    /**
      * Second phase of STE: verify a given candidate by looking for CEX inputs.
      * Returns the potential solution and whether it is to be trusted.
      */
    def validateCandidate(expr: Expr): Option[Solution] = {
      timers.validate.start()
      debug(s"Validating $expr")
      val solver = solverF.getNewSolver()

      try {
        setSolution(expr)
        solver.assertCnstr(p.pc and not(spec))
        solver.check match {
          case Some(true) =>
            // Found counterexample! Exclude this program
            val model = solver.getModel
            val cex = InExample(p.as.map(a => model.getOrElse(a, simplestValue(a.getType))))
            debug(s"Found cex $cex for $expr")
            examples +:= cex
            timers.cegisIter.stop()
            timers.cegisIter.start()
            if (restartable) {
              debug("Restarting enum...")
              it = mkEnum
            }
            None

          case Some(false) =>
            debug("Proven correct!")
            timers.cegisIter.stop()
            Some(Solution(BooleanLiteral(true), Set(), expr, isTrusted = true))

          case None =>
            if (useOptTimeout) {
              debug("Leon could not prove the validity of the resulting expression")
              // Interpret timeout in CE search as "the candidate is valid"
              Some(Solution(BooleanLiteral(true), Set(), expr, isTrusted = false))
            } else {
              // TODO: Make STE fail early when it times out when verifying 1 program?
              None
            }
        }
      } finally {
        timers.validate.stop()
        solverF.reclaim(solver)
      }
    }

    def solutionStream: Stream[Solution] = {
      timers.cegisIter.start()
      var untrusted: Seq[Solution] = Seq()
      while (!sctx.interruptManager.isInterrupted && it.hasNext) {
        val expr = it.next
        debug(s"Testing: $expr")
        if (examples.exists(testCandidate(expr)(_).contains(false))) {
          debug(s"Failed testing!")
        } else {
          debug(s"Passed testing!")
          validateCandidate(expr) foreach { sol =>
            val outerSol = sol.copy(term = innerToOuter.transform(sol.term)(Map()))
            if (sol.isTrusted) return Stream(outerSol) // Found verifiable solution, return immediately
            else untrusted :+= outerSol // Solution was not verifiable, remember it anyway.
          }
        }
      }

      untrusted.toStream // Best we could do is find unverifiable solutions

    }

  }

  def instantiateOn(implicit hctx: SearchContext, p: Problem): Traversable[RuleInstantiation] = {
    val opt = hctx.findOptionOrDefault(SynthesisPhase.optProbwiseTopdownOpt)

    List(new RuleInstantiation(s"$name (opt = $opt)") {
      def apply(hctx: SearchContext): RuleApplication = {
        try {
          val ndProgram = new NonDeterministicProgram(hctx, p, opt)
          RuleClosed (ndProgram.solutionStream)
        } catch {
          case UnsatPCException =>
            RuleFailed()
        }
      }
    })
  }
}

object ProbDrivenEnumeration extends ProbDrivenEnumerationLike("Prob. driven enum") {
  import leon.grammars.Tags
  import leon.grammars.aspects._
  def rootLabel(p: Problem, sctx: SynthesisContext) = {
    Label(p.outType).withAspect(TypeDepthBound(3))//.withAspect(Tagged(Tags.Top, 0, None))
  }
}

object ProbDrivenSimilarTermEnumeration extends ProbDrivenEnumerationLike("Prob. driven similar term enum.") {
  import purescala.Extractors.TopLevelAnds
  import leon.grammars.aspects._
  import Witnesses.Guide
  def rootLabel(p: Problem, sctx: SynthesisContext) = {
    val TopLevelAnds(clauses) = p.ws
    val guides = clauses.collect { case Guide(e) => e }
    Label(p.outType).withAspect(SimilarTo(guides, sctx.functionContext)).withAspect(DepthBound(2))
  }
}