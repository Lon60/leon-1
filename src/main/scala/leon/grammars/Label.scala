/* Copyright 2009-2015 EPFL, Lausanne */

package leon
package grammars

import purescala.Types._

import scala.collection.immutable.TreeMap

case class Label(tpe: TypeTree, aspectsMap: TreeMap[AspectKind, Aspect] = TreeMap()) extends Typed {
  val getType = tpe

  def asString(implicit ctx: LeonContext): String = {
    val ts = tpe.asString

    ts + aspects.map(_.asString).mkString
  }

  def withAspect(a: Aspect) = {
    Label(tpe, aspectsMap + (a.kind -> a))
  }

  // Strip aspects except RealTyped
  def stripAspects = {
    val map: TreeMap[AspectKind, Aspect] = aspectsMap.get(RealTypedAspectKind) match {
      case Some(tp) =>
        TreeMap(RealTypedAspectKind -> tp)
      case None =>
        TreeMap()
    }
    copy(aspectsMap = map)
  }

  def aspect(kind: AspectKind): Option[Aspect] = aspectsMap.get(kind)

  def aspects: Traversable[Aspect] = aspectsMap.map(_._2)
}
