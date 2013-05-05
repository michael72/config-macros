package org.jaylib.scala.config.json

import org.jaylib.scala.config.convert.TypeConversions
import org.jaylib.scala.config.split.Splitter
import language.existentials
import scala.collection.mutable.ListBuffer

class JsonConversions extends TypeConversions {

  protected[this] def initTabs = "  "
  protected[this] def initEndl = "\n"
  private[this] var tabsBuf = new StringBuilder(initEndl)
  protected[this] def tabs = tabsBuf.toString
  private[this] var tab = initTabs
  protected[this] def openBrace {
    tabsBuf.append(tab)
  }
  protected[this] def closingBrace {
    tabsBuf.setLength(tabsBuf.length - tab.length)
  }

  protected[this] def mkBlock(brackets: String)(unit: => Iterable[String]): String = {
    val ret = new StringBuilder
    openBrace
    ret.append(brackets(0) + tabs)
    ret.append(unit.mkString("," + tabs))
    closingBrace
    ret.append(tabs + brackets(1))
    ret.toString
  }


  protected[this] override def defaultConverter(currentType: String, childTypes: Seq[String], mapTypes: MapTypes, splitter: Splitter): String => Any = {
    val (constructor, convertersPre) = findDefaultConstructors(currentType, childTypes, mapTypes, splitter)
    params: String => {
      val converters =
        if (convertersPre != null) convertersPre
        else childTypes.map(getConverter(_, mapTypes, splitter)) // recursive type => generation of sub-converters can only be done here
      // the params are padded with empty strings when not enough parameters were provided (this also supports empty lists as initial default value)
      // the zipped.map(_(_)) calls the splitted parameters on each converter resulting in the real init parameters used for the constructor
      val sortedParams = {
        val (names, arr) = JsonConversions.replJson(params, splitter)
        // TODO really sort it according to names!
        arr.mkString(",")
      }
      val initParams = (converters, splitter(sortedParams).toSeq.padTo(converters.length, "")).zipped.map(_(_)).toArray
      constructor.newInstance(initParams.asInstanceOf[Array[Object]]: _*)
    }
  }

  override def toString(any: Any): String = any match {
    case str: String        => super.toString(any)
    case map: Map[_, _]     => mapToString(map)
    case arr: Array[_]      => seqToString(arr)
    case tr: Traversable[_] => seqToString(tr.toSeq)
    case pr: Product        => productToString(pr)
    case _                  => any.toString
  }

  def seqToString(seq: Seq[_]): String = {
    if (!seq.isEmpty) {
      val clzName = seq(0).getClass.getName
      if (clzName.startsWith("scala.lang") || clzName.startsWith("java.lang")) 
        // for primitive types the array is a one-liner
        seq.map(toString(_)).mkString("[", ", ", "]")
      
      else mkBlock("[]") {
        seq.map(toString(_))
      }
    }
    else "[]"
  }
  override def mapToString(convertMap: Map[_, _]): String = {
    mkBlock("{}") {
      convertMap.map {
        case (key, value) =>
          s"${toString(key)}: ${toString(value)}"
      }
    }
  }

  override def productToString(pr: Product): String = {
    mkBlock("{}") {
      // the fields may be unsorted -> the association must be done when reading back
      pr.getClass.getDeclaredFields.filterNot(_.isSynthetic).take(pr.productArity).map { field =>
        field.setAccessible(true)
        field.getName + ": " + toString(field.get(pr))
      }
    }
  }
}

object JsonConversions {

  def replJson(params: String, splitter: Splitter): (Array[String], Array[String]) = {
    val arr = splitter(params)
    val names = new ListBuffer[String]
    val values = new ListBuffer[String]
    arr.foreach { param =>
      val (name, value) = param.indexOf(':') match {
        case -1 => ("", param)
        case idx =>
          param.indexOf('(') match {
            case brack if (brack == -1 || brack > idx) => (param.substring(0, idx), param.substring(idx + 1))
            case _                                     => ("", param)
          }
      }
      names += name
      values += value
    }
    (names.toArray, values.toArray)
  }
}