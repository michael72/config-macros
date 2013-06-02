package org.jaylib.scala.config.split

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Stack
import org.jaylib.scala.config.convert.ConversionException
import scala.annotation.tailrec

final class Param(init: String, origChildren: Seq[Param] = Nil) {
  val part = Param.unpackString(init)
  val children: Seq[Param] =
    if (origChildren.length > 1 || (!origChildren.isEmpty && !(origChildren(0).children.isEmpty && origChildren(0).part.isEmpty())))
      origChildren
    else Nil

  def call(converter: Param => Any): Seq[Any] = {
    val ret = ListBuffer[Any]()
    val it = children.iterator
    while (it.hasNext) {
      ret += converter(it.next)
    }
    ret
  }
  def call(converters: Seq[Param => Any]): Array[Any] = {
    val ret = new Array[Any](converters.length)
    val it = converters.iterator
    var idx = 0
    while (idx < children.length) {
      ret(idx) = it.next()(children(idx))
      idx += 1
    }
    while (idx < ret.length) {
      ret(idx) = it.next()(Param.empty)
      idx += 1
    }
    ret
  }

  override def toString = origChildren match {
    case Nil => part
    case children =>
      if (part.isEmpty())
        children.mkString(",")
      else
        children.mkString(part + "(", ",", ")")
  }
}

object Param {
  val empty = new Param("")
  val emptySeq = List()
  def apply(init: String): Param = {
    val params = Stack(new ListBuffer[Param])
    val str = init.trim; // DefaultSplitter.unpacked(init)
    var inQuotes = false
    var inEscape = false
    var hasContent = false
    val currentParam = Stack("")
    var idx = 0
    var prev = 0

    while (idx < str.length) {
      val c = str(idx)
      if (inQuotes || inEscape) {
        if (inEscape) {
          inEscape = false
        }
        else {
          c match {
            case quote if (quote == '"' || quote == '\'') =>
              inQuotes = false
            case '\\' =>
              inEscape = true
            case _ =>
          }
        }
      }
      else {
        c match {
          case c if (c > ']' && c < '{') || (c > ',' && c < '[') || (c < '\"') => // ignore character
          case quote if (quote == '"' || quote == '\'') =>
            inQuotes = true
            hasContent = true
          case '\\' => // escape - skip next character
            inEscape = true
          case ',' => // next parameter
            if (idx > prev || hasContent) {
              params.top += new Param(str.substring(prev, idx))
            }
            prev = idx + 1
            hasContent = true
          case bracket if (bracket == '(' || bracket == '[' || bracket == '{') =>
            params.top += new Param(str.substring(prev, idx))
            params.push(new ListBuffer[Param])
            prev = idx + 1
            hasContent = true
          case closing if (closing == ')' || closing == ']' || closing == '}') =>
            if (idx > prev || hasContent) {
              params.top += new Param(str.substring(prev, idx))
            }
            val children = params.pop
            if (!children.isEmpty) {
              val idxTop = params.top.length - 1
              params.top.update(idxTop, new Param(params.top(idxTop).part, children))
            }
            prev = idx + 1
            hasContent = false
          case _ => // ignore any other character left
        }
      }
      idx += 1
    }
    if (params.length != 1) {
      if (params.length > 1)
        throw new RuntimeException("missing closing bracket(s) in expression " + str)
      else
        throw new RuntimeException("too many closing bracket(s) in expression " + str)
    }
    if (idx > prev || hasContent)
      params.top += new Param(str.substring(prev, idx))

    if (params.top.length > 1)
      new Param("", if (params.top.isEmpty) Nil else params.top)
    else {
      if (params.top.isEmpty)
        Param.empty
      else
        params.top(0)
    }
  }

  private[this] val internalBuf = new StringBuilder

  private[this] def replaceEscapes(str: String, start: Int, end: Int): String = {
    val idx = str.indexOf('\\', start)
    val buf = internalBuf
    if (idx == -1) {
      if (start == 0 && end == str.length - 1)
        str
      else
        str.substring(start, end)
    }
    else {
      @tailrec
      def doReplaceEscapes(str: String, idxStart: Int, idxEnd: Int) {
        val idxNew = str.indexOf('\\', idxEnd + 2)
        buf.append(str.substring(idxStart, idxEnd))
        if (idxEnd < str.length - 1) {
          buf.append(str.charAt(idxEnd + 1))
          if (idxNew == -1)
            buf.append(str.substring(idxEnd + 2, end))
          else {
            doReplaceEscapes(str, idxEnd + 2, idxNew)
          }
        }
      }
      doReplaceEscapes(str, start, idx)
      val ret = buf.toString
      buf.setLength(0)
      ret
    }
  }

  def unpackString(str: String): String = {
    if (!str.isEmpty) {
      var start = 0
      var end = str.length - 1
      while (Character.isWhitespace(str.charAt(start)) && start < end)
        start += 1
      while (Character.isWhitespace(str.charAt(end)) && end > 0)
        end -= 1

      if (start > end) ""
      else {
        val c = str.charAt(start)
        if ((c == '"' || c == '\'') && c == str.charAt(end))
          replaceEscapes(str, start + 1, end)
        else
          replaceEscapes(str, start, end + 1)
      }
    }
    else ""
  }

  def main(args: Array[String]) {
    /*println(Param("1"))
    println(Param("1, Inner(\"name\",1,2)"))*/
    /*val p = Param("Inner(Inner(1,2),3), 4")
    println(p) // =>*/
    //println(Param("""List("1", "\"2,3\"", 4)"""))
    val p = Param("{ x: 42 }")
    //config.list should be(List("1", "\"2,3\"", "4"))
    println(p)
    //println(Param("""Map("1" -> Kack("2"))"""))
    //println(Param("""XXX("1", "2,3", 4)"""))
    /*println(Param("""Map("1" -> Kack("1", "2")"""))
    println(Param("""Map("" -> List(""))""")) // List((,Param(List((-> List,Param(List()))))))*/

    // 1,2,3,4 => Param(List((1,()), (2,()),(3,()),(4,())))
    //println(Param("1,2,3,4"))

  }
}