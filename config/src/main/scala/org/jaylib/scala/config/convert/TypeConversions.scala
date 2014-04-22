package org.jaylib.scala.config.convert

import scala.collection.mutable.HashMap
import scala.ref.SoftReference
import scala.Array.canBuildFrom
import org.jaylib.scala.config.split.Splitter
import language.existentials
import org.jaylib.scala.config.split.Param
import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec

/** Converts a type to its string representation and back.
   For creation of the type from a string the `create_...` methods are used.
 
   Automatically supported types are:
   $ - collection types (immutable preferred): List, Seq, Vector, Set
   $ - all classes deriving from Product, including case classes and tuples
 
   If a new type `NewType` shall be supported, the TypeConversions has to be extended with the `create_NewType(str: String)` method.
   The conversion to a string is simply done by calling the object's toString method - Strings themselves are surrounded by quotes.
 
   If a new type (or an existing one) shall change the conversion to a string, the appendString(Any, StringBuilder)-method has to be overridden.
   
   Example: extend TypeConversions for `java.io.File`, where its String representation is mapped to the file's absolute path
 
   {{{
    val config = ConfigMacros.wrap(classOf[Config], props.getProperty, props.setProperty, new TypeConversions {
      def create_File(filename: String) = new File(filename)
      override def appendString(any: Any, buf: StringBuilder) {
        any match {
          case file: File => buf.append(file.getAbsolutePath)
          case any        => super.appendString(any, buf)
        }
      }
    })
 *  }}}
 */
class TypeConversions {
  private[this] val internalBuf = new StringBuilder
  /** Used internally to automatically convert a saved String to an Int representation.
   *  @param saved the saved integer String
   *  @return the converted Int
   */
  def create_Int(saved: String) = saved.toInt
  /** Used internally to automatically convert a saved String to a Float representation.
   *  @param saved the saved float String
   *  @return the converted Float
   */
  def create_Float(saved: String) = saved.toFloat
  /** Used internally to automatically convert a saved String to a Double representation.
   *  @param saved the saved double String
   *  @return the converted Double
   */
  def create_Double(saved: String) = saved.toDouble
  /** Used internally to automatically convert a saved String to a Boolean representation.
   *  @param saved the saved boolean String (either "true" or "false")
   *  @return the converted Boolean 
   */
  def create_Boolean(saved: String) = saved.toBoolean
  /** Used internally to automatically convert a saved String to a single Byte representation.
   *  @param saved the saved byte String
   *  @return the converted Byte
   */
  def create_Byte(saved: String) = saved.toByte
  /** Used internally to automatically convert a saved String to a short integer representation.
   *  @param saved the saved short integer String
   *  @return the converted Short
   */
  def create_Short(saved: String) = saved.toShort
  /** Used internally to automatically convert a saved String to a long integer representation.
   *  @param saved the saved long integer String
   *  @return the converted Long
   */
  def create_Long(saved: String) = saved.toLong
  /** Creates a String by removing any outer quotes from the saved String and replacing inner quotes with
   *  escapes by quotes only. */
  def create_String(saved: String) = {
    val ret = StringUtils.replaceAll(saved, "\\\"", "\"") // replace inner \" with "
    if (ret.startsWith("\""))
      ret.substring(1, ret.length - 1) // remove outer "" 
    else ret
  }

  /** Default implementation of toString for any given type using the type's toString method.
   *  This method is used by appendString. 
   *  To support own type conversions override appendString.
   */
  final def toString(any: Any) = {
    appendString(any, internalBuf)
    val ret = internalBuf.toString
    internalBuf.setLength(0)
    ret
  }
  
  /**
   * Implementation for Int (and so forth) to prevent code generation within the macro
   * that uses boxing for each generated function. The boxing is then done here at a central location.
   */
  def toString_Int(num: Int) = toString(num)
  def toString_Char(num: Char) = toString(num)
  def toString_Byte(num: Byte) = toString(num)
  def toString_Long(num: Long) = toString(num)
  def toString_Short(num: Short) = toString(num)
  def toString_Float(num: Float) = toString(num)
  def toString_Double(num: Double) = toString(num)
  def toString_Boolean(num: Boolean) = toString(num)

  /** Converts the given argument to a string and appends it to the given StringBuilder.
   *  This method may be overridden to provide an own conversion to a string. See the example 
   *  in the class comment.
   *  @param any the object to convert
   *  @param buf the buffer to append the converted object to
   */
  def appendString(any: Any, buf: StringBuilder): Unit = any match {
    case str: String =>
      buf.append('\"').append(StringUtils.replaceAll(str, "\"", "\\\"")).append('\"').toString
    case map: Map[_, _]         => mapToString(map, buf)
    case tr: Traversable[_]     => traversableToString(tr, buf)
    case pr: Product            => productToString(pr, buf)
    // use of append for basic types is more efficient than boxing and conversion to String
    case i: java.lang.Integer   => buf.append(i.intValue)
    case b: java.lang.Boolean   => buf.append(b.booleanValue)
    case f: java.lang.Float     => buf.append(f.floatValue)
    case l: java.lang.Long      => buf.append(l.longValue)
    case d: java.lang.Double    => buf.append(d.doubleValue)
    case s: java.lang.Short     => buf.append(s.shortValue)
    case b: java.lang.Byte      => buf.append(b.byteValue)
    case c: java.lang.Character => buf.append(c.charValue)
    case _                      => buf.append(any)
  }

  /** Helper method to append to the mkString of an Iterator directly to a buffer.
   *  @param prefix the prefix to append first to the buffer
   *  @param it the iterator where each element and appended to the buffer is separated by " ," .
   *  @param buf the buffer to append the data to. 
   */
  protected[this] def mkString(prefix: String, it: Iterator[_], buf: StringBuilder) {
    @tailrec
    def mkStringPart(it: Iterator[_]) {
      appendString(it.next, buf)
      if (it.hasNext) {
        buf.append(", ")
        mkStringPart(it)
      }
    }
    buf.append(prefix).append('(')
    if (it.hasNext)
      mkStringPart(it)
    buf.append(')')
  }

  /**
   * Appends the String representation of a map to a buffer.
   * The String representation contains the String representation of each key/value-pair, 
   * separated by an arrow "->".
   * 
   * @param map the map to convert
   * @param buf the buffer to append the result to.
   */
  def mapToString(map: Map[_, _], buf: StringBuilder) {
    @tailrec
    def mkStringPart(it: Iterator[(_, _)], buf: StringBuilder) {
      val keyValue = it.next
      appendString(keyValue._1, buf)
      buf.append(" -> ")
      appendString(keyValue._2, buf)
      if (it.hasNext) {
        buf.append(", ")
        mkStringPart(it, buf)
      }
    }
    buf.append(getClassName(map.getClass)).append('(')
    val it = map.iterator
    if (it.hasNext)
      mkStringPart(it, buf)
    buf.append(')')
  }

  /**
   * Appends each element of a Traversable to a buffer.
   * @param tr the traversable containing the elements
   * @param buf the buffer to append to
   */
  def traversableToString(tr: Traversable[_], buf: StringBuilder) {
    mkString(getClassName(tr.getClass), tr.toIterator, buf)
  }

  /**
   * Appends the contents of a product to a buffer.
   * @param pr the product
   * @param buf the buffer to append the product elements to.
   */
  def productToString(pr: Product, buf: StringBuilder) {
    val isTuple = pr.getClass.getName.startsWith("scala.Tuple")
    mkString(if (isTuple) "" else pr.productPrefix, pr.productIterator, buf)
  }

  /**
   * Tries to convert the given parameters for a given type or types.
   * Example:
   * {{{
   * x: Int = tryConvert("x", "Int", splitter)("23") // x = 23
   * }}}
   * @param the name of the parameter (variable name) that is converted. This is used for error output when the conversion fails.
   * @param types the types or classnames that the parameters should be converted to.
   * @param splitter the splitter to seperate the parameter types.
   * @param params the parameters to convert. 
   * @throws ConversionException if the conversion fails.
   */
  def tryConvert(name: String, types: String, splitter: Splitter)(params: String): Any = {
    try {
      if (types.equals("String"))
        create_String(params) // Strings are a little "sensitive" data - they may contain brackets etc. which may confuse the parameter conversion 
      else
    	convertAny(types, splitter)(Param(params))
    }
    catch {
      case t: Throwable =>
        throw new ConversionException(name, t)
    }
  }

  protected[this]type MapTypes = HashMap[String, Seq[String]]

  /** Tries to convert a given type string with parameters to the result.
   *  The result has to be casted to the appropriate type in the calling method.
   */
  def convertAny(types: String, splitter: Splitter)(params: Param): Any = {
    getConverter(types, new MapTypes(), splitter)(params)
  }

  /** Gets either the cached converter or creates one depending on the expected types.
   * @param types the types the converter should generate
   * @param mapTypes helper cache used for recursive and re-used types (e.g. in lists)
   * @param splitter the splitter to seperate the supplied types
   */
  protected[this] def getConverter(types: String, mapTypes: MapTypes, splitter: Splitter): Param => Any = {
    val (currentType, childTypes) = splitter.splitParamType(types)
    val genType = if (types.length > currentType.length && types.charAt(currentType.length) == '[') types else currentType
    // get the stored type conversion or else provide the conversion function
    TypeConversions.getOrElseUpdate(getClass, types, {
      converterImpl(currentType, mapTypes.getOrElseUpdate(genType, childTypes), mapTypes, splitter)
    })
  }

  protected[this] def getClassName(clz: Class[_]): String = TypeConversions.getNameOrElseUpdate(clz.getName)

  /** In case the used type is an inner class, Class.forName and introspection only
   *  work with the names of the outer/inner class separated by '$'
   */
  protected[config] def getCreateString(types: String, splitter: Splitter): String = {
    val arr = Array(types)
    convertCreateString(arr, types, splitter)
    arr(0)
  }
  /** Creates the '$'-separation for outer/inner class.
   */
  protected[this] def convertCreateString(orig: Array[String], types: String, splitter: Splitter) {
    if (types.contains(".")) {
      val (currentType, children) = splitter.splitParamType(types)
      val arr = currentType.split("\\.")
      val replType = arr.indexWhere(str => !str.isEmpty && Character.isUpperCase(str.charAt(0))) match {
        case -1 => currentType
        case 0  => arr.mkString("$")
        case idx =>
          val splitted = arr.splitAt(idx)
          splitted._1.mkString(".") + "." + splitted._2.mkString("$")
      }
      if (replType != currentType) {
        orig(0) = StringUtils.replaceAll(orig(0), currentType, replType)
      }
      children.foreach(child => convertCreateString(orig, child, splitter))
    }
  }

  /** Helper to convert a saved map back to the original Map representation.
   */
  protected[this] def mapConverter(childTypes: Iterable[String], mapTypes: MapTypes, splitter: Splitter): Param => Map[_, _] = {
    val Seq(keyConverter, valueConverter) = childTypes.map(getConverter(_, mapTypes, splitter))
    params: Param =>
      params.children.map {
        param =>
          val Array(key, value) = param.toString.split("->")
          // convert the key to the key-type and the value to the value-type
          // create a map of keys and values afterwards
          (keyConverter(Param(key.trim)), valueConverter(Param(value.trim)))
      }.toMap
  }

  
  /** Helper to convert a saved sequence (List, Vector, Seq or Set) back to its original representation.
   */
  protected[this] def sequenceConverter(currentType: String, childTypes: Seq[String], mapTypes: MapTypes, splitter: Splitter): Param => Iterable[_] = {
    val converter = getConverter(childTypes(0), mapTypes, splitter)

    currentType match {

      // convert each element of the split string-list
      case "List" =>
        params: Param => params.call(converter).toList

      // convert each element of the split string-list
      case vect if (vect == "Vector" || vect == "Seq") =>
        params: Param => params.call(converter).toVector

      // convert each element of the split string-list
      case "Set" =>
        params: Param => params.call(converter).toSet
    }
  }

  /** For a given type and the supplied child types:
   *  get the constructor for the type where all children fit best.
   *  If there is only one constructor, the one constructor is returned.
   */
  protected[this] def findDefaultConstructors(currentType: String, childTypes: Seq[String], mapTypes: MapTypes, splitter: Splitter) = {
    val clz = if (currentType.isEmpty) // Tuple
      Class.forName("scala.Tuple" + childTypes.length)
    else // we might have a case class here
      Class.forName(getCreateString(currentType, splitter))
    val constructors = clz.getConstructors.toList

    if (childTypes.isEmpty) {
      // search for the best fitting constructor (smallest number of parameters & does not contain current type)
      (if (constructors.length == 1) constructors
      else constructors.filterNot(_.getParameterTypes.map(_.getName).contains(currentType)).sortBy(_.getParameterTypes.length).toList) match {
        case constructor :: any => (clz, constructor, constructor.getParameterTypes.map(getClassName).map(getConverter(_, mapTypes, splitter)).toSeq)
        case Nil                => throw new Exception("could not find appropriate constructor")
      }
    }
    else {
      // find will deliver Some(constructor) where the parameters fit the childTypes, 
      // alternatively we'll take the one and only constructor
      (if (constructors.length == 1) Some(constructors(0))
      else constructors.find(_.getParameterTypes.map(_.getName).toList == childTypes)) match {

        case Some(constructor) => if (!childTypes.exists(_.indexOf(currentType) != -1))
          (clz, constructor, childTypes.map(getConverter(_, mapTypes, splitter)).toSeq)
        else (clz, constructor, null) // null -> the converter for a recursive type is generated in the function call below
        case None => throw new Exception("could not find appropriate constructor")
      }
    }
  }

  /** The default converter is used when all other converters fail.
   *
   *  It is suitable for Product (Tuple and case classes) and all other classes that have constructor containing the child types.
   */
  protected[this] def defaultConverter(currentType: String, childTypes: Seq[String], mapTypes: MapTypes, splitter: Splitter): Param => Any = {

    val (_, constructor, convertersPre) = findDefaultConstructors(currentType, childTypes, mapTypes, splitter)
    params: Param =>
      {
        val converters =
          if (convertersPre != null) convertersPre
          else {
            if (childTypes.isEmpty) Nil
            else childTypes.map(getConverter(_, mapTypes, splitter)) // recursive type => generation of sub-converters can only be done here
          }
        // the params are padded with empty strings when not enough parameters were provided (this also supports empty lists as initial default value)
        constructor.newInstance(params.call(converters).asInstanceOf[Array[Object]]: _*)
      }

  }

  /** Depending on the the type and if an direct "create_"-method is available, return the "create_"-Method or try to apply
   *  other conversions (default-constructor, product, sequence or map).
   */
  protected[this] def converterImpl(currentType: String, childTypes: Seq[String], mapTypes: MapTypes, splitter: Splitter) = {
    val names = TypeConversions.creatorFromClassName(splitter.shortNameOf(currentType)) :: TypeConversions.creatorFromClassName(currentType) :: Nil

    this.getClass.getMethods.find(m => names.contains(m.getName)) match {
      case Some(method) => {
        // explicit create_method is preferred
        params: Param =>
          method.invoke(this, params.toString)
      }
      case None => {
        // no create method found: try some introspection
        if (TypeConversions.DefaultTypes.contains(currentType)) {
          if (currentType == "Map") {
            // special handling for a Map
            mapConverter(childTypes, mapTypes, splitter)
          }
          else {
            // for Sequence-Types we have the same converter
            sequenceConverter(currentType, childTypes, mapTypes, splitter)
          }
        }
        else {
          try {
            defaultConverter(currentType, childTypes, mapTypes, splitter)
          }
          catch {
            case t: Throwable => throw new UnsupportedOperationException("unsupported type: " + currentType, t)
          }
        }
      }
    }
  }
}

object TypeConversions {
  val DefaultTypes = Seq("Map", "List", "Set", "Seq", "Vector")
  val CreatorPrefix = "create_"

  def creatorFromClassName(clzName: String) = CreatorPrefix + clzName.replaceAll("[\\[\\],.]", "_")
  def classNameFromCreator(creatorFun: String) =
    if (creatorFun.startsWith(CreatorPrefix)) creatorFun.substring(CreatorPrefix.length).replace('_', '.')
    else throw new IllegalArgumentException(creatorFun + " is no creator name! Needs to start with " + CreatorPrefix)

  private[this] class Cache[T <: AnyRef] {
    private[this] var ref = new SoftReference(null.asInstanceOf[T])
    /** This is _not_ threadsafe. If it shall be made threadsafe, it has to be handled in the `case None`-block.
     *  Also the access to elements in T (in this case the Map) have to be made thread-safe, e.g. by using ConcurrentMap
     */
    def getOrCreate(creator: => T): T = {
      ref.get match {
        case None =>
          // the cache has been cleaned up or is not (yet) available: create it
          val cache = creator
          ref = new SoftReference(cache)
          cache
        case Some(cache) => cache
      }
    }
    def get = ref.get
  }
  private[this]type NameCache = HashMap[String, String]
  private[this]type ConverterCache = HashMap[(Class[_], String), Param => Any]
  private[this] val nameCaches = new Cache[NameCache]
  private[this] val converterCaches = new Cache[ConverterCache]

  /** Caches the conversion method for (String => Any).
   *  The conversion from String to the actual type is usually only performed once on initialization, when the config elements are filled.
   *  Afterwards it will probably never be touched again. Hence the whole map, that caches the conversions is referenced by a weak reference only
   *  and will at some time be cleaned up completely.
   *
   *  \attention this method is _not_ threadsafe!
   */
  private def getOrElseUpdate(caller: Class[_], param: String, createConverter: => Param => Any): Param => Any = {
    converterCaches.getOrCreate(new ConverterCache).getOrElseUpdate((caller, param), createConverter)
  }

  val replacements = Seq(("java.lang.", ""), ("scala.collection.immutable.$colon$colon", "List"), ("scala.collection.immutable.", ""))

  /** Caches the name conversion of a class name.
   *
   *  \attention this method is _not_ threadsafe!
   */
  private def getNameOrElseUpdate(clz: String): String =
    nameCaches.getOrCreate(new NameCache).getOrElseUpdate(clz,
      StringUtils.replaceFirst(clz, replacements) match {
        case pkg if (pkg.indexOf('.') != -1) => {
          // scala.List -> List, ...
          if (pkg.startsWith("scala.") && pkg.indexOf('.', 6) != -1)
            pkg.substring(6)
          else pkg
        }
        case sub =>
          val idx = sub.indexOf('$')
          if (idx == -1) sub.capitalize // for java std types - int -> Int, float -> Float...
          else sub.substring(0, idx) // name contains a '$' for an inner class -> only take the beginning
      })

  /** Clear the caches explicitly.
   */
  def clearCaches {
    nameCaches.get match {
      case Some(cache) => cache.clear
      case None        => // nothing to do
    }
    converterCaches.get match {
      case Some(cache) => cache.clear
      case None        => // nothing to do
    }
  }
}


