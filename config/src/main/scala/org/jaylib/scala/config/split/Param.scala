package org.jaylib.scala.config.split

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Stack
import org.jaylib.scala.config.convert.ConversionException
import scala.annotation.tailrec

/** Holds hierarchical and recursive parameters.
 *
 *  {{{
 *  Param("List(1,2,3)")
 *  Param(part="List", children=Param(part="1")::Param(part="2")::Param(part="3"))
 *  }}}
 *
 */
final class Param(init: String, origChildren: Seq[Param] = Nil) {
  /** The top part of the current parameter.
   */
  val part = Param.unpackString(init)
  /** The children parameters needed for the constructor parameters of the current type.
   */
  val children: Seq[Param] =
    if (origChildren.length > 1 || (!origChildren.isEmpty && !(origChildren(0).children.isEmpty && origChildren(0).part.isEmpty())))
      origChildren
    else Nil

  /** Converts the children of this parameter given a converter to a list of converted values.
   */
  def call(converter: Param => Any): Seq[Any] = {
    val ret = new ListBuffer[Any]()
    val it = children.iterator
    while (it.hasNext) {
      ret += converter(it.next)
    }
    ret
  }
  /** Converts a sequence of parameters to a resulting array
   *  which can be used as constructor arguments for the
   *  surrounding object.
   */
  def call(converters: Seq[Param => Any]): Array[Any] = {
    val ret = new Array[Any](converters.length)
    val it = converters.iterator
    val childIt = children.iterator
    var idx = 0
    while (childIt.hasNext) {
      ret(idx) = it.next()(childIt.next)
      idx += 1
    }
    while (idx < ret.length) {
      ret(idx) = it.next()(Param.empty)
      idx += 1
    }
    ret
  }

  /** Converts the parameters back to a String representation.
   */
  override def toString = origChildren match {
    case empty if empty.isEmpty => part
    case children               => children.mkString(part + "(", ",", ")")
  }
}

object Param {
  val empty = new Param("")
  val emptySeq = List()

  /** Creates a Param given a String.
   */
  def apply(init: String): Param = {
    val params = Stack(new ListBuffer[Param])
    val str = init.trim

    @tailrec
    def parseNext(idx: Int, prev: Int, inQuotes: Boolean) {
      def addNext(opening: Boolean) {
        if (idx > prev || opening)
          params.top += new Param(str.substring(prev, idx))
      }
      if (idx < str.length) {
        str(idx) match {
          case c if (c > ']' && c < '{') || (c > ',' && c < '[') || (c < '\"') => // ignore character
            parseNext(idx + 1, prev, inQuotes)
          case quote if (quote == '"' || quote == '\'') =>
            parseNext(idx + 1, prev, !inQuotes) // begin or end quotes
          case '\\' => // escape - skip next character
            parseNext(idx + 2, prev, inQuotes)
          case ',' if (!inQuotes) => // next parameter
            addNext(false)
            parseNext(idx + 1, idx + 1, false)
          case bracket if (bracket == '(' || bracket == '[' || bracket == '{') && !inQuotes =>
            addNext(true)
            params.push(new ListBuffer[Param])
            parseNext(idx + 1, idx + 1, false)
          case closing if (closing == ')' || closing == ']' || closing == '}') && !inQuotes =>
            addNext(false)
            val children = params.pop
            if (!children.isEmpty) {
              val idxTop = params.top.length - 1
              params.top.update(idxTop, new Param(params.top(idxTop).part, children))
            }
            parseNext(idx + 1, idx + 1, false)
          case _ => // ignore any other character left
            parseNext(idx + 1, prev, inQuotes)
        }
      } else
        addNext(false)
    }

    parseNext(0, 0, false)

    if (params.length != 1) {
      if (params.length > 1)
        throw new RuntimeException("missing closing bracket(s) in expression " + str)
      else
        throw new RuntimeException("too many closing bracket(s) in expression " + str)
    }

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

  private[this] def substr(str: String, start: Int, end: Int): String = {
    if (start == 0 && end == str.length - 1)
      str
    else
      str.substring(start, end)
  }

  @tailrec
  def findNextQuote(str: String, idx: Int, quote: Char): Int = {
    if (idx < str.length) {
      str(idx) match {
        case '\\'              => findNextQuote(str, idx + 2, quote)
        case d if (d == quote) => idx
        case _                 => findNextQuote(str, idx + 1, quote)
      }
    } else -1
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
        str.charAt(start) match {
          case quote if ((quote == '"' || quote == '\'') && findNextQuote(str, start + 1, quote) == end) =>
            substr(str, start + 1, end)
          case _ =>
            substr(str, start, end + 1)
        }
      }
    } else ""
  }
}