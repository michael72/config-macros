package org.jaylib.scala.config.split

/**
 * Helper to split string representation of parameters to arrays of parameters, also nested parameters.
 */
trait Splitter {
 /**
   * For an input String Type[Params,...,Params] returns the (Type, [Params,...,Params]) as Tuple of String and Array[String].
   * Params can also consist of nested parameters.
   * Example:
   * "Items(1,(4,5),6,(7,8))" yields the result Tuple: ("Items", Seq("1", "(4,5)", "6", "(7,8)"))
   */
  def splitParamType(str: String) : (String, Seq[String])
  /**
   * For an input String Type[Params,...,Params] returns the [Params,...,Params] as Array[String].
   * Params can also consist of nested parameters.
   * Example:
   * "Items((1,2),(4,5),(6,7))" yields the result Array: Array("(1,2)", "(4,5)", "(6,7)")
   */
  def split(str: String) : Seq[String]
  /**
   * Shortens the input classname by its packages.
   * Example:
   * "java.io.File" yields "File" and
   * "java.util.List[java.io.File]" yields "List[File]" 
   */
  def shortNameOf(clzName: String) : String
}