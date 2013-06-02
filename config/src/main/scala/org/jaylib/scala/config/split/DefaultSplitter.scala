package org.jaylib.scala.config.split

import scala.annotation.tailrec
import scala.collection.mutable.{ ListBuffer }
import scala.collection.immutable.Stack

/** Default implementation of the splitter that works on Strings where the items are separated by comma.
 */
class DefaultSplitter extends Splitter {
  /** For an input String Type[Params,...,Params] returns the (Type, [Params,...,Params]) as Tuple of String and Array[String].
   *  Params can also consist of nested parameters.
   *  Example:
   *  "Items(1,(4,5),6,(7,8))" yields the result Tuple: ("Items", Array("1", "(4,5)", "6", "(7,8)"))
   */
  override def splitParamType(str: String): (String, Seq[String]) = {
    val s = str.trim
    val idxFirstBracket = findFirstBracket(str, 0)
    if (idxFirstBracket != -1) {
      val arr = buildSeqFromString(s.substring(idxFirstBracket + 1, s.length - 1))
      (s.substring(0, idxFirstBracket), arr)
    }
    else // no brackets found - we only have the type
      (s, Nil)
  }
  
  @tailrec
  private[this] def findFirstBracket(str: String, idx: Int) : Int = {
    str.charAt(idx) match {
      case bracket if (bracket == '(' || bracket == '[') => idx
      case ',' => -1
      case _ => if (idx == str.length-1) -1 else findFirstBracket(str, idx+1)
    }
  }

  /** For an input String Type[Params,...,Params] returns the [Params,...,Params] as Array[String].
   *  Params can also consist of nested parameters.
   *  Example:
   *  "Items((1,2),(4,5),(6,7))" yields the result Array: Array("(1,2)", "(4,5)", "(6,7)")
   */
  @inline
  override def split(str: String) = buildSeqFromString(DefaultSplitter.unpacked(str))

  
  /** Shortens the input classname by its packages.
   *  Example:
   *  "java.io.File" yields "File" and
   *  "java.util.List[java.io.File]" yields "List[File]"
   */
  override def shortNameOf(clzName: String): String = {
    val part = new StringBuilder
    val buf = new StringBuilder
    clzName.foreach {
      _ match {
        case c if (c > ']' && c < '{') || (c > '.' && c < '[') =>
          part.append(c)
        case '.' => part.setLength(0) // take only the part after the last dot
        case separator if (DefaultSplitter.NAME_SEPARATORS.contains(separator)) =>
          buf.append(part.toString)
          buf.append(separator)
          part.setLength(0)
        case ' ' =>
          buf.append(part.toString)
          part.setLength(0)
        case any =>
          part.append(any)
      }
    }
    buf.append(part.toString).toString // add the rest of the part to the return value
  }

  private[this] def buildSeqFromString(strUnpacked: String): Seq[String] = {
    if (!strUnpacked.isEmpty) {
      val lb = new ListBuffer[String]()
      buildSeq(strUnpacked, 0, lb)
      lb.toSeq
    }
    else
      Nil

  }

  /** Small parsing done here - the content of Strings in '"' is not considered, also
   *  the bracket level is considered.
   *  For example: findNext(((1,2),3), Stack(','), 0) should deliver the index of the 2nd comma, because
   *  the first is in a pair of extra brackets.
   */
  @tailrec
  private[this] def findNext(str: String, search: Stack[Char], idx: Int): Int = {
    if (idx < str.length) {
      str.charAt(idx) match {
        // performance hack with ASCII table - first work on the characters that are not searched
        // searched characters (up to now) are: comma, the brackets, quotes and backspace
        case c if (c > ']' && c < '{') || (c > ',' && c < '[') || (c < '\"') =>
          findNext(str, search, idx + 1)
        case found if (found == search.top) =>
          val newSearch = search.pop
          if (newSearch.isEmpty) idx // the initially searched item is found -> return the index
          else findNext(str, newSearch, idx + 1) // only closing bracket or quote was found -> go on searching
        case quote if (quote == '"' || quote == '\'') =>
          findNext(str, search.push(quote), idx + 1) // search next fitting quote
        case '\\' => // escape - skip next character
          findNext(str, search, idx + 2)
        case bracket if (bracket == '(' || bracket == '[' || bracket == '{') =>
          findNext(str, search.push(DefaultSplitter.CLOSING_BRACKETS(DefaultSplitter.OPENING_BRACKETS.indexOf(bracket))), idx + 1) // search closing bracket*/
        case closing if (closing == ')' || closing == ']' || closing == '}') =>
          throw new IllegalArgumentException("unmatched closing bracket in " + str.substring(0, idx) + " /*--->*/ " + str.substring(idx) + ", search is " + (if (search.isEmpty) "empty" else search.toList.mkString("[", ",", "]")))
        case _ =>
          findNext(str, search, idx + 1)
      }
    }
    else {
      // end of string is reached - the comma itself is not expected at the end, hence ',' on the stack is normal,
      // but another item indicates that a closing bracket is missing or there is one opening bracket too many
      if (search.length > 1) throw new IllegalArgumentException(s"The expression '${str}' is missing a closing '${search.pop}'")
      -1
    }
  }

  /** Builds the ListBuffer in res with the parameters separated by ','.
   */
  @tailrec
  private[this] def buildSeq(str: String, startIdx: Int, res: ListBuffer[String]) {
    findNext(str, DefaultSplitter.SEARCH, startIdx) match {
      case -1 => res += str.substring(startIdx).trim // no further entries found - done.
      case idx =>
        res += str.substring(startIdx, idx).trim // add the current result
        buildSeq(str, idx + 1, res) // and go to next item
    }
  }
}

object DefaultSplitter {
  val NAME_SEPARATORS = "[,(".toSeq
  val OPENING_BRACKETS = "([{".toSeq
  val CLOSING_BRACKETS = ")]}".toSeq
  val BRACKETS = OPENING_BRACKETS ++ CLOSING_BRACKETS
  val SEARCH = Stack(',')

  /**
   * Search for the last non-whitespace character in a String.
   * @param str the String to search in
   * @param index the position to start searching on the right side (usually str.length - 1)
   * @return None, when the (trimmed) String is empty, otherwise the Character in an Option 
   */
  @tailrec
  def findLast(str: String, index: Int): Option[Char] = {
    if (index == -1) None
    else {
      str.charAt(index) match {
        case space if Character.isWhitespace(space) => findLast(str, index - 1)
        case c                                      => Some(c)
      }
    }
  }
  /**
   * Unpacks an expression String with brackets.
   * Example unpacked("fun(1,2,3)") will return "1,2,3", but
   * unpacked("x, fun(1,2,3)") will return the same String (the String was already unpacked).
   * @param str string to unpack
   * @return unpacked string
   */
  def unpacked(str: String) = findLast(str, str.length - 1) match {
    // check if the expression ends with a closing bracket - if so (and no parameter is preceeding): unpack the string
    case Some(closing) if DefaultSplitter.CLOSING_BRACKETS.contains(closing) =>
      val idxBracket = str.indexOf(DefaultSplitter.OPENING_BRACKETS(DefaultSplitter.CLOSING_BRACKETS.indexOf(closing)))
      if (str.lastIndexOf(',', idxBracket) == -1) // no parameters before the bracket -> unpack the brackets
        str.substring(idxBracket + 1, str.lastIndexOf(closing))
      else
        str
    case _ => str
  }

}