package leon.integration.solvers

import org.scalatest.FunSuite
import org.scalatest.Matchers
import leon.test.helpers.ExpressionsDSL
import leon.solvers.string.StringSolver
import leon.purescala.Common.FreshIdentifier
import leon.purescala.Common.Identifier
import leon.purescala.Expressions._
import leon.purescala.Definitions._
import leon.purescala.DefOps
import leon.purescala.ExprOps
import leon.purescala.Types._
import leon.purescala.TypeOps
import leon.purescala.Constructors._
import leon.synthesis.rules.{StringRender, TypedTemplateGenerator}
import scala.collection.mutable.{HashMap => MMap}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.concurrent.Timeouts
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.SpanSugar._
import org.scalatest.FunSuite
import org.scalatest.concurrent.Timeouts
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.SpanSugar._
import leon.purescala.Types.Int32Type
import leon.test.LeonTestSuiteWithProgram
import leon.synthesis.SourceInfo
import leon.synthesis.CostModels
import leon.synthesis.graph.AndNode
import leon.synthesis.SearchContext
import leon.synthesis.Synthesizer
import leon.synthesis.SynthesisSettings
import leon.synthesis.RuleApplication
import leon.synthesis.RuleClosed
import leon.evaluators._
import leon.LeonContext
import leon.synthesis.rules.DetupleInput
import leon.synthesis.Rules
import leon.solvers.ModelBuilder
import scala.language.implicitConversions
import leon.LeonContext
import leon.test.LeonTestSuiteWithProgram
import leon.test.helpers.ExpressionsDSL
import leon.synthesis.disambiguation.InputRecCoverage
import leon.test.helpers.ExpressionsDSLProgram
import leon.test.helpers.ExpressionsDSLVariables
import leon.purescala.Extractors._
import org.scalatest.PrivateMethodTester.PrivateMethod
import org.scalatest.PrivateMethodTester
import org.scalatest.matchers.Matcher
import org.scalatest.matchers.MatchResult

class InputRecCoverageSuite extends LeonTestSuiteWithProgram with Matchers with ScalaFutures with ExpressionsDSLProgram with ExpressionsDSLVariables with PrivateMethodTester  {
  val sources = List("""
    |import leon.lang._
    |import leon.collection._
    |import leon.lang.synthesis._
    |import leon.annotation._
    |
    |object InputRecCoverageSuite {
    |  def dummy = 2
    |  def f(l: List[String]): String    = l match { case Nil() => "[]" case Cons(a, b) => "[" + a + frec(b) }
    |  def frec(l: List[String]): String = l match { case Nil() => ""   case Cons(a, b) => "," + a + frec(b) }
    |  
    |  // Slightly different version of f with one inversion not caught by just covering examples.
    |  def g(l: List[String]): String    = l match { case Nil() => "[]" case Cons(a, b) => "[" + a + grec(b) }
    |  def grec(l: List[String]): String = l match { case Nil() => ""   case Cons(a, b) => "," + grec(b) + a }
    |}""".stripMargin)
    
  
  def haveOneWhich[T](pred: T => Boolean, predStr: String = "")(implicit m: Manifest[Iterable[T]]) =  Matcher { (left: Iterable[T]) =>  
    MatchResult( 
      left exists pred,
      s"No element $predStr among ${left.mkString(", ")}",
      s"All elements of ${left.mkString(", ")} $predStr")
  }
  
  def eval(f: FunDef)(e: Seq[Expr])(implicit ctx: LeonContext, program: Program): Expr = {
    val d = new DefaultEvaluator(ctx, program)
    d.eval(functionInvocation(f, e)).result.get
  }
  
  test("InputRecCoverage should expand covering examples to make them rec-covering."){ ctxprogram =>
    implicit val (c, p) = ctxprogram
    val f = funDef("InputRecCoverageSuite.f")
    val frec = funDef("InputRecCoverageSuite.frec")
    val g = funDef("InputRecCoverageSuite.g")
    val reccoverage = new InputRecCoverage(f, Set(f, frec))
    reccoverage.result().map(x => x(0)) should haveOneWhich({(input: Expr) =>
      eval(f)(Seq(input)) != eval(g)(Seq(input))
    }, "make f and g differ")
  }
}