/* Copyright 2009-2014 EPFL, Lausanne */

package leon
package purescala

import Trees._

object Extractors {
  import Common._
  import TypeTrees._
  import TypeTreeOps._
  import Definitions._
  import Extractors._
  import Constructors._
  import TreeOps._

  object UnaryOperator {
    def unapply(expr: Expr) : Option[(Expr,(Expr)=>Expr)] = expr match {
      case Not(t) => Some((t,Not(_)))
      case UMinus(t) => Some((t,UMinus))
      case SetCardinality(t) => Some((t,SetCardinality))
      case MultisetCardinality(t) => Some((t,MultisetCardinality))
      case MultisetToSet(t) => Some((t,MultisetToSet))
      case SetMin(s) => Some((s,SetMin))
      case SetMax(s) => Some((s,SetMax))
      case CaseClassSelector(cd, e, sel) => Some((e, CaseClassSelector(cd, _, sel)))
      case CaseClassInstanceOf(cd, e) => Some((e, CaseClassInstanceOf(cd, _)))
      case TupleSelect(t, i) => Some((t, tupleSelect(_, i)))
      case ArrayLength(a) => Some((a, ArrayLength))
      case Lambda(args, body) => Some((body, Lambda(args, _)))
      case Forall(args, body) => Some((body, Forall(args, _)))
      case (ue: UnaryExtractable) => ue.extract
      case _ => None
    }
  }

  trait UnaryExtractable {
    def extract: Option[(Expr, (Expr)=>Expr)];
  }

  object BinaryOperator {
    def unapply(expr: Expr) : Option[(Expr,Expr,(Expr,Expr)=>Expr)] = expr match {
      case Equals(t1,t2) => Some((t1,t2,Equals.apply))
      case Implies(t1,t2) => Some((t1,t2, implies))
      case Plus(t1,t2) => Some((t1,t2,Plus))
      case Minus(t1,t2) => Some((t1,t2,Minus))
      case Times(t1,t2) => Some((t1,t2,Times))
      case Division(t1,t2) => Some((t1,t2,Division))
      case Modulo(t1,t2) => Some((t1,t2,Modulo))
      case LessThan(t1,t2) => Some((t1,t2,LessThan))
      case GreaterThan(t1,t2) => Some((t1,t2,GreaterThan))
      case LessEquals(t1,t2) => Some((t1,t2,LessEquals))
      case GreaterEquals(t1,t2) => Some((t1,t2,GreaterEquals))
      case ElementOfSet(t1,t2) => Some((t1,t2,ElementOfSet))
      case SubsetOf(t1,t2) => Some((t1,t2,SubsetOf))
      case SetIntersection(t1,t2) => Some((t1,t2,SetIntersection))
      case SetUnion(t1,t2) => Some((t1,t2,SetUnion))
      case SetDifference(t1,t2) => Some((t1,t2,SetDifference))
      case Multiplicity(t1,t2) => Some((t1,t2,Multiplicity))
      case MultisetIntersection(t1,t2) => Some((t1,t2,MultisetIntersection))
      case MultisetUnion(t1,t2) => Some((t1,t2,MultisetUnion))
      case MultisetPlus(t1,t2) => Some((t1,t2,MultisetPlus))
      case MultisetDifference(t1,t2) => Some((t1,t2,MultisetDifference))
      case mg@MapGet(t1,t2) => Some((t1,t2, (t1, t2) => MapGet(t1, t2).setPos(mg)))
      case MapUnion(t1,t2) => Some((t1,t2,MapUnion))
      case MapDifference(t1,t2) => Some((t1,t2,MapDifference))
      case MapIsDefinedAt(t1,t2) => Some((t1,t2, MapIsDefinedAt))
      case ArraySelect(t1, t2) => Some((t1, t2, ArraySelect))
      case Let(binders, e, body) => Some((e, body, (e: Expr, b: Expr) => Let(binders, e, b)))
      case Require(pre, body) => Some((pre, body, Require))
      case Ensuring(body, id, post) => Some((body, post, (b: Expr, p: Expr) => Ensuring(b, id, p)))
      case Assert(const, oerr, body) => Some((const, body, (c: Expr, b: Expr) => Assert(c, oerr, b)))
      case (ex: BinaryExtractable) => ex.extract
      case _ => None
    }
  }

  trait BinaryExtractable {
    def extract: Option[(Expr, Expr, (Expr, Expr)=>Expr)];
  }

  object NAryOperator {
    def unapply(expr: Expr) : Option[(Seq[Expr],(Seq[Expr])=>Expr)] = expr match {
      case fi @ FunctionInvocation(fd, args) => Some((args, (as => FunctionInvocation(fd, as).setPos(fi))))
      case mi @ MethodInvocation(rec, cd, tfd, args) => Some((rec +: args, (as => MethodInvocation(as.head, cd, tfd, as.tail).setPos(mi))))
      case fa @ Application(caller, args) => Some((caller +: args), (as => Application(as.head, as.tail).setPos(fa)))
      case CaseClass(cd, args) => Some((args, CaseClass(cd, _)))
      case And(args) => Some((args, and))
      case Or(args) => Some((args, or))
      case FiniteSet(args) =>
        Some((args.toSeq,
              { newargs =>
                if (newargs.isEmpty) {
                  FiniteSet(Set()).setType(expr.getType)
                } else {
                  FiniteSet(newargs.toSet)
                }
              }
            ))
      case FiniteMap(args) => {
        val subArgs = args.flatMap{case (k, v) => Seq(k, v)}
        val builder: (Seq[Expr]) => Expr = (as: Seq[Expr]) => {
          val (keys, values, isKey) = as.foldLeft[(List[Expr], List[Expr], Boolean)]((Nil, Nil, true)){
            case ((keys, values, isKey), rExpr) => if(isKey) (rExpr::keys, values, false) else (keys, rExpr::values, true)
          }
          assert(isKey)
          val tpe = (keys, values) match {
            case (Seq(), Seq()) => expr.getType
            case _ =>
              MapType(
                bestRealType(leastUpperBound(keys.map  (_.getType)).get),
                bestRealType(leastUpperBound(values.map(_.getType)).get)
              )
          }
          FiniteMap(keys.zip(values)).setType(tpe)
        }
        Some((subArgs, builder))
      }
      case FiniteMultiset(args) => Some((args, FiniteMultiset))
      case ArrayUpdated(t1, t2, t3) => Some((Seq(t1,t2,t3), (as: Seq[Expr]) => ArrayUpdated(as(0), as(1), as(2))))
      case FiniteArray(elems, default, length) => {
        val fixedElems: Seq[(Int, Expr)] = elems.toSeq
        val all: Seq[Expr] = fixedElems.map(_._2) ++ default ++ Seq(length)
        Some((all, (as: Seq[Expr]) => {
          val tpe = leastUpperBound(as.map(_.getType))
                                  .map(ArrayType(_))
                                  .getOrElse(expr.getType)
          val (newElems, newDefault, newSize) = default match {
            case None => (as.init, None, as.last)
            case Some(_) => (as.init.init, Some(as.init.last), as.last)
          }
          FiniteArray(
            fixedElems.zip(newElems).map(p => (p._1._1, p._2)).toMap,
            newDefault,
            newSize).setType(tpe)
        }))
      }

      case Tuple(args) => Some((args, Tuple))
      case IfExpr(cond, thenn, elze) => Some((Seq(cond, thenn, elze), (as: Seq[Expr]) => IfExpr(as(0), as(1), as(2))))
      case MatchLike(scrut, cases, builder) => Some((
        scrut +: cases.flatMap { 
          case SimpleCase(_, e) => Seq(e)
          case GuardedCase(_, e1, e2) => Seq(e1, e2) 
        }, 
        (es: Seq[Expr]) => {
          var i = 1
          val newcases = for (caze <- cases) yield caze match {
            case SimpleCase(b, _) => i+=1; SimpleCase(b, es(i-1)) 
            case GuardedCase(b, _, _) => i+=2; GuardedCase(b, es(i-2), es(i-1)) 
          }

          builder(es(0), newcases)
        }
      ))
      case Passes(in, out, cases) => Some((
        in +: out +: cases.flatMap { _.expressions },
        { case Seq(in, out, es@_*) => {
          var i = 0
          val newcases = for (caze <- cases) yield caze match {
            case SimpleCase(b, _) => i+=1; SimpleCase(b, es(i-1)) 
            case GuardedCase(b, _, _) => i+=2; GuardedCase(b, es(i-1), es(i-2)) 
          }

          Passes(in, out, newcases)
        }}
      ))
      case LetDef(fd, body) =>
        fd.body match {
          case Some(b) =>
            (fd.precondition, fd.postcondition) match {
              case (None, None) =>
                  Some((Seq(b, body), (as: Seq[Expr]) => {
                    fd.body = Some(as(0))
                    LetDef(fd, as(1))
                  }))
              case (Some(pre), None) =>
                  Some((Seq(b, body, pre), (as: Seq[Expr]) => {
                    fd.body = Some(as(0))
                    fd.precondition = Some(as(2))
                    LetDef(fd, as(1))
                  }))
              case (None, Some((pid, post))) =>
                  Some((Seq(b, body, post), (as: Seq[Expr]) => {
                    fd.body = Some(as(0))
                    fd.postcondition = Some((pid, as(2)))
                    LetDef(fd, as(1))
                  }))
              case (Some(pre), Some((pid, post))) =>
                  Some((Seq(b, body, pre, post), (as: Seq[Expr]) => {
                    fd.body = Some(as(0))
                    fd.precondition = Some(as(2))
                    fd.postcondition = Some((pid, as(3)))
                    LetDef(fd, as(1))
                  }))
            }

          case None => //case no body, we still need to handle remaining cases
            (fd.precondition, fd.postcondition) match {
              case (None, None) =>
                  Some((Seq(body), (as: Seq[Expr]) => {
                    LetDef(fd, as(0))
                  }))
              case (Some(pre), None) =>
                  Some((Seq(body, pre), (as: Seq[Expr]) => {
                    fd.precondition = Some(as(1))
                    LetDef(fd, as(0))
                  }))
              case (None, Some((pid, post))) =>
                  Some((Seq(body, post), (as: Seq[Expr]) => {
                    fd.postcondition = Some((pid, as(1)))
                    LetDef(fd, as(0))
                  }))
              case (Some(pre), Some((pid, post))) =>
                  Some((Seq(body, pre, post), (as: Seq[Expr]) => {
                    fd.precondition = Some(as(1))
                    fd.postcondition = Some((pid, as(2)))
                    LetDef(fd, as(0))
                  }))
            }
        }
      case (ex: NAryExtractable) => ex.extract
      case _ => None
    }
  }

  trait NAryExtractable {
    def extract: Option[(Seq[Expr], (Seq[Expr])=>Expr)];
  }

  object TopLevelOrs { // expr1 AND (expr2 AND (expr3 AND ..)) => List(expr1, expr2, expr3)
    def unapply(e: Expr): Option[Seq[Expr]] = e match {
      case Or(exprs) =>
        Some(exprs.flatMap(unapply(_)).flatten)
      case e =>
        Some(Seq(e))
    }
  }
  object TopLevelAnds { // expr1 AND (expr2 AND (expr3 AND ..)) => List(expr1, expr2, expr3)
    def unapply(e: Expr): Option[Seq[Expr]] = e match {
      case And(exprs) =>
        Some(exprs.flatMap(unapply(_)).flatten)
      case e =>
        Some(Seq(e))
    }
  }

  object IsTyped {
    def unapply[T <: Typed](e: T): Option[(T, TypeTree)] = Some((e, e.getType))
  }

  object FiniteLambda {
    def unapply(lambda: Lambda): Option[(Expr, Seq[(Expr, Expr)])] = {
      val args = lambda.args.map(_.toVariable)
      lazy val argsTuple = if (lambda.args.size > 1) Tuple(args) else args.head

      def rec(body: Expr): Option[(Expr, Seq[(Expr, Expr)])] = body match {
        case _ : IntLiteral | _ : UMinus | _ : BooleanLiteral | _ : GenericValue | _ : Tuple |
             _ : CaseClass | _ : FiniteArray | _ : FiniteSet | _ : FiniteMap | _ : Lambda =>
          Some(body -> Seq.empty)
        case IfExpr(Equals(tpArgs, key), expr, elze) if tpArgs == argsTuple =>
          rec(elze).map { case (dflt, mapping) => dflt -> ((key -> expr) +: mapping) }
        case _ => None
      }

      rec(lambda.body)
    }

    def apply(dflt: Expr, els: Seq[(Expr, Expr)], tpe: FunctionType): Lambda = {
      val args = tpe.from.zipWithIndex.map { case (tpe, idx) =>
        ValDef(FreshIdentifier(s"x${idx + 1}").setType(tpe), tpe)
      }

      assume(els.isEmpty || !tpe.from.isEmpty, "Can't provide finite mapping for lambda without parameters")

      lazy val (tupleArgs, tupleKey) = if (tpe.from.size > 1) {
        val tpArgs = Tuple(args.map(_.toVariable))
        val key = (x: Expr) => x
        (tpArgs, key)
      } else { // note that value is lazy, so if tpe.from.size == 0, foldRight will never access (tupleArgs, tupleKey)
        val tpArgs = args.head.toVariable
        val key = (x: Expr) => {
          if (isSubtypeOf(x.getType, tpe.from.head)) x
          else if (isSubtypeOf(x.getType, TupleType(tpe.from))) x.asInstanceOf[Tuple].exprs.head
          else throw new RuntimeException("Can't determine key tuple state : " + x + " of " + tpe)
        }
        (tpArgs, key)
      }

      val body = els.toSeq.foldRight(dflt) { case ((k, v), elze) =>
        IfExpr(Equals(tupleArgs, tupleKey(k)), v, elze)
      }

      Lambda(args, body)
    }
  }
  object MatchLike {
    def unapply(m : MatchLike) : Option[(Expr, Seq[MatchCase], (Expr, Seq[MatchCase]) => Expr)] = {
      Option(m) map { m => 
        (m.scrutinee, m.cases, m match {
          case _ : MatchExpr  => matchExpr
          case _ : Gives      => gives
        })
      }
    }
  }

  object SimpleCase {
    def apply(p : Pattern, rhs : Expr) = MatchCase(p, None, rhs)
    def unapply(c : MatchCase) = c match {
      case MatchCase(p, None, rhs) => Some((p, rhs))
      case _ => None
    }
  }
  
  object GuardedCase {
    def apply(p : Pattern, g: Expr, rhs : Expr) = MatchCase(p, Some(g), rhs)
    def unapply(c : MatchCase) = c match {
      case MatchCase(p, Some(g), rhs) => Some((p, g, rhs))
      case _ => None
    }
  }
  
  object Pattern {
    def unapply(p : Pattern) : Option[(
      Option[Identifier], 
      Seq[Pattern], 
      (Option[Identifier], Seq[Pattern]) => Pattern
    )] = Option(p) map {
      case InstanceOfPattern(b, ct)       => (b, Seq(), (b, _)  => InstanceOfPattern(b,ct))
      case WildcardPattern(b)             => (b, Seq(), (b, _)  => WildcardPattern(b))
      case CaseClassPattern(b, ct, subs)  => (b, subs,  (b, sp) => CaseClassPattern(b, ct, sp))
      case TuplePattern(b,subs)           => (b, subs,  (b, sp) => TuplePattern(b, sp))
      case LiteralPattern(b, l)           => (b, Seq(), (b, _)  => LiteralPattern(b, l))
    }
  }

  object UnwrapTuple {
    def unapply(e : Expr) : Option[Seq[Expr]] = Option(e) map {
      case Tuple(subs) => subs
      case other => Seq(other)
    }
  }

  object UnwrapTuplePattern {
    def unapply(p : Pattern) : Option[Seq[Pattern]] = Option(p) map {
      case TuplePattern(_,subs) => subs
      case other => Seq(other)
    }
  }
  
  object LetPattern {
    def apply(patt : Pattern, value: Expr, body: Expr) : Expr = {
      patt match {
        case WildcardPattern(Some(binder)) => Let(binder, value, body)
        case _ => MatchExpr(value, List(SimpleCase(patt, body)))
      }
    }

    def unapply(me : MatchExpr) : Option[(Pattern, Expr, Expr)] = {
      if (me eq null) None else { me match {
        case MatchExpr(scrut, List(SimpleCase(pattern, body))) if !aliased(pattern.binders, variablesOf(scrut)) =>
          Some(( pattern, scrut, body ))
        case _ => None
      }}
    }
  }

  object LetTuple {
    def unapply(me : MatchExpr) : Option[(Seq[Identifier], Expr, Expr)] = {
      if (me eq null) None else { me match {
        case LetPattern(TuplePattern(None,subPatts), value, body) if
            subPatts forall { case WildcardPattern(Some(_)) => true; case _ => false } => 
          Some((subPatts map { _.binder.get }, value, body ))
        case _ => None
      }}
    }
  }   

}
