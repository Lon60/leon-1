/* Copyright 2009-2016 EPFL, Lausanne */

import leon.annotation._
import leon.lang._

import leon.io.{
  FileInputStream => FIS
}

/*
 * This test was reduced from a bigger one. A bug caused GenC to crash
 * when converting `processImage` from Scala to its IR because there was
 * a mismatch between the function argument name and the one used in its
 * body: bh$2 vs bh$1. This mismatch in identifier was generated by
 * RemoveVCPhase, for some obscure reasons.
 */
object RegressionTest1 {

  case class BitmapHeader(width: Int, height: Int) {
    require(0 <= width && 0 <= height)
  }

  def maybeReadBitmapHeader(fis: FIS)(implicit state: leon.io.State): BitmapHeader = {
    require(fis.isOpen)
    BitmapHeader(16, 16)
  }

  def process(fis: FIS)(implicit state: leon.io.State): Boolean = {
    require(fis.isOpen)

    def processImage(bh: BitmapHeader): Boolean = {
      if (bh.width > 0) true
      else false
    }

    val bitmapHeader = maybeReadBitmapHeader(fis)
    processImage(bitmapHeader)
  }

  def _main(): Int = {
    implicit val state = leon.io.newState
    val input = FIS.open("input.bmp")

    if (input.isOpen) {
      process(input)
      ()
    }

    0
  }

  @extern
  def main(args: Array[String]): Unit = _main()

}


