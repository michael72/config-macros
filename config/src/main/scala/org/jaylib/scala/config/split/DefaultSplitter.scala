package org.jaylib.scala.config.split

import scala.annotation.tailrec
import scala.collection.mutable.{ListBuffer, Stack}

/**
 * Default implementation of the splitter that works on Strings where the items are separated by comma.
 */
class DefaultSplitter extends Splitter {
  /**
   * For an input String Type[Params,...,Params] returns the (Type, [Params,...,Params]) as Tuple of String and Array[String].
   * Params can also consist of nested parameters.
   * Example:
   * "Items(1,(4,5),6,(7,8))" yields the result Tuple: ("Items", Array("1", "(4,5)", "6", "(7,8)"))
   */
  override def splitParamType(str: String) = {
    val s = str.trim
    val idxFirstComma = { val f = findNext(s, Stack(','), 0); if (f == -1) s.length else f }
    val idxCaseBracket = s.indexOf('(')
    val idxGenBracket = s.indexOf('[')
    val idxFirstBracket = if (idxGenBracket != -1 && (idxGenBracket < idxCaseBracket || idxCaseBracket == -1)) idxGenBracket else idxCaseBracket
    if (idxFirstBracket != -1 && idxFirstBracket < idxFirstComma) {
      val arr = buildSeqFromString(s.substring(idxFirstBracket+1, s.length-1))
      (s.substring(0, idxFirstBracket), arr)
    }
    else // no brackets found - we only have the type
      (s, Array[String]())
  }

  /**
   * For an input String Type[Params,...,Params] returns the [Params,...,Params] as Array[String].
   * Params can also consist of nested parameters.
   * Example:
   * "Items((1,2),(4,5),(6,7))" yields the result Array: Array("(1,2)", "(4,5)", "(6,7)")
   */
  override def apply(str: String) = splitParamsImpl(str)

  /**
   * Shortens the input classname by its packages.
   * Example:
   * "java.io.File" yields "File" and
   * "java.util.List[java.io.File]" yields "List[File]" 
   */
  override def shortNameOf(clzName: String) : String = {
    val part = new StringBuilder
    val buf = new StringBuilder
    clzName.foreach {
      _ match {
        case '.' => part.setLength(0) // take only the part after the last dot
        case ' ' => buf.append(part.toString)
        	part.setLength(0)
        case separator if (DefaultSplitter.BRACKETS.contains(separator) || separator == ',') =>
          buf.append(part.toString)
          buf.append(separator)
          part.setLength(0)
        case any =>
          part.append(any)
      }
    }
    buf.append(part.toString) // add the rest of the part to the return value
    buf.toString
  }

  private[this] def splitParamsImpl(str: String) = {
    val s = str.trim
    val strUnpacked = 
      if (s.endsWith(")")) s.substring(s.indexOf("(") + 1, s.length() - 1) 
      else {
        if (s.endsWith("]")) s.substring(s.indexOf("[") + 1, s.length() - 1)
        else s
      }
    buildSeqFromString(strUnpacked)
  }
  
  private[this] def buildSeqFromString(strUnpacked: String) = {
    if (!strUnpacked.isEmpty) {
      val lb = new ListBuffer[String]()
      buildSeq(strUnpacked, 0, lb)
      lb.toArray
    }
    else
      Array[String]()
    
  }

  /**
   * Small parsing done here - the content of Strings in '"' is not considered, also
   * the bracket level is considered.
   * For example: findNext(((1,2),3), Stack(','), 0) should deliver the index of the 2nd comma, because
   * the first is in a pair of extra brackets.
   */
  @tailrec
  private def findNext(str: String, search: Stack[Char], idx: Int): Int = {
    if (idx < str.length) {
      str.charAt(idx) match {
        case found if (found == search.top) =>
          search.pop
          if (search.isEmpty) idx // the initially searched item is found -> return the index
          else findNext(str, search, idx + 1) // only closing bracket or quote was found -> go on searching
        case bracket if (bracket == '(' || bracket == '[') =>
          search.push(if (bracket == '(') ')' else ']') // search closing bracket
          findNext(str, search, idx + 1)
        case quote if (quote == '"' || quote == '\'') =>
          search.push(quote) // search next fitting quote
          findNext(str, search, idx + 1)
        case '\\' => // escape - skip next character
          findNext(str, search, idx + 2)
        case any if (any != ')' && any != ']') =>
          findNext(str, search, idx + 1)
        case _ =>
          throw new IllegalArgumentException("unmatched closing bracket in " + str)
      }
    }
    else {
      // end of string is reached - the comma itself is not expected at the end, hence ',' on the stack is normal,
      // but another item indicates that a closing bracket is missing or there is one opening bracket too many
      if (search.length > 1) throw new IllegalArgumentException(s"The expression '${str}' is missing a closing '${search.pop}'")
      -1
    }
  }

  /**
   * Builds the ListBuffer in res with the parameters separated by ','.
   */
  @tailrec
  private def buildSeq(str: String, startIdx: Int, res: ListBuffer[String]) {
    findNext(str, Stack(','), startIdx) match {
      case -1 => res += str.substring(startIdx).trim // no further entries found - done.
      case idx =>
        res += str.substring(startIdx, idx).trim // add the current result
        buildSeq(str, idx + 1, res) // and go to next item
    }
  }
}

object DefaultSplitter {
  val OPENING_BRACKETS = '(' :: '[' :: Nil
  val CLOSING_BRACKETS = ')' :: ']' :: Nil
  val BRACKETS = OPENING_BRACKETS ::: CLOSING_BRACKETS
}