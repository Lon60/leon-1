import leon._
import leon.mem._
import leon.higherorder._
import leon.lang._
import leon.annotation._
import leon.instrumentation._
import leon.invariant._
import leon.collection._
import leon.runtimeDriver._

object SortingnConcat {  
  abstract class LList2

  case class SCons1(x316 : BigInt, tailFun1 : Stream2) extends LList2
  
  case class SNil1() extends LList2
  
  case class Stream2(lfun1 : () => (LList2, BigInt))
  
  def pullMintime(l : List[BigInt]): (List[BigInt], BigInt) = {
    val bd4 = l match {
      case Nil() =>
        (l, BigInt(2))
      case Cons(x, xs) =>
        val e34 = pullMintime(xs)
        val scr8 = BigInt(1) + e34._2
        val mc7 = e34._1 match {
          case Nil() =>
            (List[BigInt](x), BigInt(4) + scr8)
          case nxs @ Cons(y, ys) =>
            val mc6 = if (x <= y) {
              (Cons[BigInt](x, nxs), BigInt(3))
            } else {
              (Cons[BigInt](y, Cons[BigInt](x, ys)), BigInt(4))
            }
            (mc6._1, (BigInt(5) + mc6._2) + scr8)
        }
        (mc7._1, BigInt(5) + mc7._2)
    }
    (bd4._1, bd4._2)
  }
  
  def sorttime(l : List[BigInt]): (LList2, BigInt) = {
    val e15 = pullMintime(l)
    val scr6 = BigInt(1) + e15._2
    val bd1 = e15._1 match {
      case Cons(x, xs) =>
        (SCons1(x, Stream2(() => {
          val e18 = sorttime(xs)
          (e18._1, BigInt(1) + e18._2)
        })), BigInt(7) + scr6)
      case _ =>
        (SNil1(), BigInt(3) + scr6)
    }
    (bd1._1, bd1._2)
  }
  
  def concattime(l1 : List[BigInt], l2 : LList2): (LList2, BigInt) = {
    val bd6 = l1 match {
      case Cons(x, xs) =>
        (SCons1(x, Stream2(() => {
          val e48 = concattime(xs, l2)
          (e48._1, BigInt(1) + e48._2)
        })), BigInt(7))
      case Nil() =>
        (SNil1(), BigInt(4))
    }
    (bd6._1, bd6._2)
  }
  
  def kthMintime(l : Stream2, k : BigInt): (BigInt, BigInt) = {
    val lr = lookup[LList2](List(4878, l))
    val scr1 = if (lr._1) {
      (lr._2, BigInt(1))
    } else {
      val e22 = Stream.listtime(l)
      (update[LList2](List(4878, l), e22._1), BigInt(1) + e22._2)
    }
    val bd2 = scr1._1 match {
      case SCons1(x, xs36) =>
        val mc2 = if (k == BigInt(1)) {
          (x, BigInt(2))
        } else {
          val e27 = kthMintime(xs36, k - BigInt(1))
          (e27._1, BigInt(4) + e27._2)
        }
        (mc2._1, (BigInt(4) + mc2._2) + scr1._2)
      case SNil1() =>
        (BigInt(0), BigInt(3) + scr1._2)
    }
    (bd2._1, bd2._2)
  }

   def main(args: Array[String]): Unit = {
    import scala.util.Random
    val rand = Random

    val points = (10 to 200 by 10) ++ (100 to 2000 by 100) ++ (1000 to 10000 by 1000)
    val size = points.map(x => BigInt(2*x)).toList
    
    var ops = List[() => BigInt]()
    var orb = List[() => BigInt]()
    points.foreach { i =>
      val input = {
        (1 to i).foldLeft[List[BigInt]](Nil()) { (f, n) =>
          Cons(n, f)  
        }
      }
      ops :+= {() => sorttime(input)._2}
      orb :+= {() => 11 * i + 35}
    }
    run(size, ops, orb, "sort")

    ops = List[() => BigInt]()
    orb = List[() => BigInt]()
    points.foreach { i =>
      val input = {
        (1 to i).foldLeft[List[BigInt]](Nil()) { (f, n) =>
          Cons(n, f)  
        }
      }
      ops :+= {() => kthMintime(input, 10)._2}
      orb :+= {() => 11 * i + 35}
    }
    run(size, ops, orb, "kthMintime")

  }
}

object LList {
  def size(thiss : SortingnConcat.LList2): BigInt = thiss match {
    case SortingnConcat.SCons1(_, t379) =>
      BigInt(1) + size(Star[SortingnConcat.LList2](Stream.listtime(t379)._1).*)
    case _ =>
      BigInt(0)
  }
}

object Stream {
  def listtime(thiss : SortingnConcat.Stream2): (SortingnConcat.LList2, BigInt) = {
    val e32 = thiss.lfun1()
    (e32._1, BigInt(2) + e32._2)
  }
  
  def size(thiss : SortingnConcat.Stream2): BigInt = LList.size(Star[SortingnConcat.LList2](listtime(thiss)._1).*)
}
