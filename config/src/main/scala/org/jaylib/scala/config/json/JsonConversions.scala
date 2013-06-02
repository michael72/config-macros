package org.jaylib.scala.config.json

import org.jaylib.scala.config.convert.TypeConversions
import org.jaylib.scala.config.split.Splitter
import language.existentials
import scala.collection.mutable.ListBuffer
import org.jaylib.scala.config.split.Param

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

  protected[this] def mkBlock(brackets: String, buf: StringBuilder, it: => Iterable[String]) {

    openBrace
    val inBrackets = it.mkString("," + tabs) 
    buf.append(brackets(0) + tabs)
    buf.append(inBrackets)
    closingBrace
    buf.append(tabs + brackets(1))
  }

  override protected[this] def mapConverter(childTypes: Iterable[String], mapTypes: MapTypes, splitter: Splitter): Param => Map[_, _] = {
    val Seq(keyConverter, valueConverter) = childTypes.map(getConverter(_, mapTypes, splitter))
    params: Param =>
      params.children.map {
        param =>
          val str = param.toString
          val colon = str.indexOf(':')
          val key = str.substring(0, colon)
          val value = str.substring(colon + 1)
          // convert the key to the key-type and the value to the value-type
          // create a map of keys and values afterwards
          (keyConverter(Param(key.trim)), valueConverter(Param(value.trim)))
      }.toMap
  }

  protected[this] override def defaultConverter(currentType: String, childTypes: Seq[String], mapTypes: MapTypes, splitter: Splitter): Param => Any = {
    val (constructor, convertersPre) = findDefaultConstructors(currentType, childTypes, mapTypes, splitter)
    params: Param => {
      val converters =
        if (convertersPre != null) convertersPre
        else childTypes.map(getConverter(_, mapTypes, splitter)) // recursive type => generation of sub-converters can only be done here
      // the params are padded with empty strings when not enough parameters were provided (this also supports empty lists as initial default value)
      // the zipped.map(_(_)) calls the splitted parameters on each converter resulting in the real init parameters used for the constructor
      val sortedParams = {
        val (names, arr) = JsonConversions.replJson(params, splitter)
        // TODO really sort it according to names!
        Param(arr.mkString("(", ",", ")"))
      }
      constructor.newInstance(sortedParams.call(converters).asInstanceOf[Array[Object]]: _*)
    }
  }

  override def appendString(any: Any, buf: StringBuilder) {
    any match {
      case str: String        => super.appendString(any, buf)
      case arr: Array[_]      => seqToString(arr, buf)
      case tr: Traversable[_] => seqToString(tr.toSeq, buf)
      case _                  => super.appendString(any, buf)
    }
  }

  def seqToString(seq: Seq[_], buf: StringBuilder) {
    if (!seq.isEmpty) {
      val clzName = seq(0).getClass.getName
      if (clzName.startsWith("scala.lang") || clzName.startsWith("java.lang"))
        // for primitive types the array is a one-liner
    	buf.append(seq.map(toString(_)).mkString("[", ", ", "]"))
      else mkBlock("[]", buf, seq.map(toString(_)))
    }
    else buf.append("[]")
  }
  override def mapToString(convertMap: Map[_, _], buf: StringBuilder) {
    mkBlock("{}", buf,
      convertMap.map {
        case (key, value) =>
          new StringBuilder(toString(key)).append(": ").append(toString(value)).toString
      })
  }

  override def productToString(pr: Product, buf: StringBuilder) {
    mkBlock("{}", buf,
      // the fields may be unsorted -> the association must be done when reading back
      pr.getClass.getDeclaredFields.filterNot(_.isSynthetic).take(pr.productArity).map { field =>
        field.setAccessible(true)
        field.getName + ": " + toString(field.get(pr))
      }
    )
  }
}

object JsonConversions {

  def replJson(params: Param, splitter: Splitter): (Array[String], Array[String]) = {
    val names = new ListBuffer[String]
    val values = new ListBuffer[String]
    params.children.foreach { p =>
      val param = p.part
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