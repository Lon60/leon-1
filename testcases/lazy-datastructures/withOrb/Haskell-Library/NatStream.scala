package stream

import leon._
import lang._
import annotation._
import instrumentation._
import mem._
import higherorder._
import collection._
import invariant._
import StreamLibrary._

object NatStream {

  def nthElemInNatsFromM(n: BigInt, M: BigInt) = {
    require(n >= 0)
    getnthElem(n, natsFromn(M))
  } ensuring(_ => time <= ? * n + ?) // Orb result: ??

}
