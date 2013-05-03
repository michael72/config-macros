package org.jaylib.scala.config.convert

import scala.collection.mutable.HashMap
import scala.ref.SoftReference
import scala.Array.canBuildFrom
import org.jaylib.scala.config.split.Splitter

/**
 * Converts a type to its string representation and back.
 * For creation of the type from a string the `create_...` methods are used.
 * If a new type `NewType` shall be supported, the TypeConversions has to be extended with the `create_NewType(str: String)` method.
 * The conversion to a string is simply done by calling the obect's toString method, excepting the String-type itself, which is surrounded by quotes.
 * If a new type (or an existing one) shall change the conversion to a string, the toString(Any)-method has to be overwritten.
 *
 * Example: extend TypeConversions for `java.io.File`, where its String representation is mapped to the file's absolute path
 *
{{{
val conversions = new TypeConversions {
  def create_File(absolutePath: String) = new java.io.File(absolutePath)
  override def toString(any: Any) = any match {
    case file: java.io.File => file.getAbsolutePath
    case other              => super.toString(other)
  }
}
}}}
 */
class TypeConversions {
  def create_Int(str: String) = str.toInt
  def create_Float(str: String) = str.toFloat
  def create_Double(str: String) = str.toDouble
  def create_Boolean(str: String) = str.toBoolean
  def create_String(str: String) = {
    val ret = str.replace("\\\"", "\"") // replace inner \" with "
    if (ret.startsWith("\""))
      ret.substring(1, ret.length - 1) // remove outer "" 
    else ret
  }
  def create_Byte(str: String) = str.toByte
  def create_Short(str: String) = str.toShort
  def create_Long(str: String) = str.toLong

  /**
   * Default implementation of toString for any given type using the type's toString method.
   * For more refined toString operations, this method should be refined with the type as parameter and
   * a specialized string conversion.
   */
  def toString(any: Any): String = any match {
    case str: String => s""""${str.replace("\"", "\\\"")}"""" // attach outer "" and replace inner " with \"
    case map: Map[_, _] => mapToString(map)
    case tr: Traversable[_] => tr.map(toString(_)).mkString(s"${getClassName(tr.getClass)}(", ", ", ")")
    case pr: Product => productToString(pr)
    case _ => any.toString
  }

  def mapToString(map: Map[_, _]): String =
    map.iterator.map { case (key, value) => s"${toString(key)} -> ${toString(value)}" }.mkString(s"${getClassName(map.getClass)}(", ", ", ")")

  def productToString(pr: Product): String = {
    val isTuple = pr.getClass.getPackage.getName == "scala"
    val prefix = if (isTuple) "" else pr.productPrefix
    pr.productIterator.map(toString(_)).mkString(prefix + "(", ", ", ")")
  }

  def tryConvert(name: String, types: String, splitter: Splitter)(params: String): Any = {
    try {
      convertAny(types, splitter)(params)
    } catch {
      case t: Throwable =>
        throw new ConversionException(name, t)
    }
  }

  private[this]type MapTypes = HashMap[String, Array[String]]
  /**
   * Tries to convert a given type string with parameters to the result.
   * The result has to be casted to the appropriate type in the calling method.
   */
  def convertAny(types: String, splitter: Splitter)(params: String): Any = {
    getConverter(types, new MapTypes(), splitter)(params)
  }

  private[this] def getConverter(types: String, mapTypes: MapTypes, splitter: Splitter): String => Any = {
    val (currentType, childTypes) = splitter.splitParamType(types)
    // get the stored type conversion or else provide the conversion function
    TypeConversions.getOrElseUpdate(types, {
      converterImpl(currentType, mapTypes.getOrElseUpdate(currentType, childTypes), mapTypes, splitter)
    })
  }

  private[this] def getClassName(clz: Class[_]): String = TypeConversions.getNameOrElseUpdate(clz.getName)

  /**
   * In case the used type is an inner class, Class.forName and introspection only
   * work with the names of the outer/inner class separated by '$'
   */
  private[config] def getCreateString(types: String, splitter: Splitter): String = {
    val arr = Array(types)
    convertCreateString(arr, types, splitter)
    arr(0)
  }
  /**
   * Creates the '$'-separation for outer/inner class.
   */
  private[this] def convertCreateString(orig: Array[String], types: String, splitter: Splitter) {
    if (types.contains(".")) {
      val (currentType, children) = splitter.splitParamType(types)
      val arr = currentType.split("\\.")
      val replType = arr.indexWhere(str => !str.isEmpty && Character.isUpperCase(str.charAt(0))) match {
        case -1 => currentType
        case 0 => arr.mkString("$")
        case idx =>
          val splitted = arr.splitAt(idx)
          splitted._1.mkString(".") + "." + splitted._2.mkString("$")
      }
      if (replType != currentType) {
        orig(0) = orig(0).replace(currentType, replType)
      }
      children.foreach(child => convertCreateString(orig, child, splitter))
    }
  }

  private[this] def converterImpl(currentType: String, childTypes: Array[String], mapTypes: MapTypes, splitter: Splitter) = {
    val names = TypeConversions.creatorFromClassName(splitter.shortNameOf(currentType)) :: TypeConversions.creatorFromClassName(currentType) :: Nil

    this.getClass.getMethods.find(m => names.contains(m.getName)) match {
      case Some(method) => {
        // explicit create_method is preferred
        params: String => method.invoke(this, params)
      }
      case None => {
        // no create method found: try some introspection
        if (TypeConversions.DefaultTypes.contains(currentType)) {
          if (currentType == "Map") {
            // special handling for a Map
            val Array(keyConverter, valueConverter) = childTypes.map(getConverter(_, mapTypes, splitter))
            params: String =>
              splitter(params).map { kv =>
                val Array(key, value) = kv.split("->")
                // convert the key to the key-type and the value to the value-type
                // create a map of keys and values afterwards
                (keyConverter(key.trim), valueConverter(value.trim))
              }.toMap

          } else {
            // for Sequence-Types we have the same converter
            val converter = getConverter(childTypes(0), mapTypes, splitter)
            
            currentType match {
              
              case "List" => 
                // convert each element of the split string-list
                params: String => splitter(params).map(converter).toList
              
              case vect if (vect == "Vector" || vect == "Seq") => 
                // convert each element of the split string-list
                params: String => splitter(params).map(converter).toVector
              
              case "Set" => 
                // convert each element of the split string-list
                params: String => splitter(params).map(converter).toSet
              
            }
          }
        } else {
          try {
            import language.existentials
            val constructors = if (currentType.isEmpty) // Tuple
              Class.forName("scala.Tuple" + childTypes.length).getConstructors
            else // we might have a case class here
              Class.forName(getCreateString(currentType, splitter)).getConstructors

            val (constructor, convertersPre) = {
              if (childTypes.isEmpty) {
                // search for the best fitting constructor (smallest number of parameters & does not contain current type)
                (if (constructors.length == 1) constructors.toList
                else constructors.filterNot(_.getParameterTypes.map(_.getName).contains(currentType)).sortBy(_.getParameterTypes.length).toList) match {
                  case constructor :: any => (constructor, constructor.getParameterTypes.map(getClassName).map(getConverter(_, mapTypes, splitter)))
                  case Nil => throw new Exception("could not find appropriate constructor")
                }
              } else {
                // find will deliver Some(constructor) where the parameters fit the childTypes, 
                // alternatively we'll take the one and only constructor
                (if (constructors.length == 1) Some(constructors(0))
                else constructors.find(_.getParameterTypes.map(_.getName).toList == childTypes)) match {
                  case Some(constructor) => (constructor, null) // null -> the converter for a recursive type is generated in the function call below  
                  case None => throw new Exception("could not find appropriate constructor")
                }
              }
            }

            params: String => {
              val converters =
                if (convertersPre != null) convertersPre
                else childTypes.map(getConverter(_, mapTypes, splitter)) // recursive type => generation of sub-converters can only be done here
              // the params are padded with empty strings when not enough parameters were provided (this also supports empty lists as initial default value)
              // the zipped.map(_(_)) calls the splitted parameters on each converter resulting in the real init parameters used for the constructor
              val initParams = (converters, splitter(params).padTo(converters.length, "")).zipped.map(_(_)).toArray
              constructor.newInstance(initParams.asInstanceOf[Array[Object]]: _*)
            }
          } catch {
            case t: Throwable => throw new UnsupportedOperationException("unsupported type: " + currentType, t)
          }
        }
      }
    }
  }
}

object TypeConversions {
  val DefaultTypes = Set("Map", "List", "Set", "Seq", "Vector")
  val CreatorPrefix = "create_"

  def creatorFromClassName(clzName: String) = CreatorPrefix + clzName.replaceAll("[\\[\\],.]", "_")
  def classNameFromCreator(creatorFun: String) =
    if (creatorFun.startsWith(CreatorPrefix)) creatorFun.substring(CreatorPrefix.length).replace('_', '.')
    else throw new IllegalArgumentException(creatorFun + " is no creator name! Needs to start with " + CreatorPrefix)

  private[this] class Cache[T <: AnyRef] {
    private[this] var ref = new SoftReference(null.asInstanceOf[T])
    /**
     * This is _not_ threadsafe. If it shall be made threadsafe, it has to be handled in the `case None`-block.
     * Also the access to elements in T (in this case the Map) have to be made thread-safe, e.g. by using ConcurrentMap
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
  private[this] type NameCache = HashMap[String, String]
  private[this] type ConverterCache = HashMap[String, String => Any]
  private[this] val nameCaches = new Cache[NameCache]
  private[this] val converterCaches = new Cache[ConverterCache]

  /**
   * Caches the conversion method for (String => Any).
   * The conversion from String to the actual type is usually only performed once on initialization, when the config elements are filled.
   * Afterwards it will probably never be touched again. Hence the whole map, that caches the conversions is referenced by a weak reference only
   * and will at some time be cleaned up completely.
   *
   * \attention this method is _not_ threadsafe!
   */
  private def getOrElseUpdate(param: String, createConverter: => String => Any): String => Any = {
    converterCaches.getOrCreate(new ConverterCache).getOrElseUpdate(param, createConverter)
  }

  /**
   * Caches the name conversion of a class name.
   *
   * \attention this method is _not_ threadsafe!
   */
  private def getNameOrElseUpdate(clz: String): String =
    nameCaches.getOrCreate(new NameCache).getOrElseUpdate(clz,
      clz.replace("java.lang.", "").
        replace("scala.collection.immutable.", "").
        replace("$colon$colon", "List").
        replaceAll("\\$.*", "") match {
          case pkg if (pkg.contains(".")) => {
            // scala.List -> List, ...
            if (pkg.startsWith("scala.") && !pkg.substring(6).contains("."))
              pkg.substring(6)
            else pkg
          }
          case primitive => primitive.capitalize // for java std types - int -> Int, float -> Float...
        })

  /**
   * Clear the caches explicitly.
   */
  def clearCaches {
    nameCaches.get match {
      case Some(cache) => cache.clear
      case None => // nothing to do
    }
    converterCaches.get match {
      case Some(cache) => cache.clear
      case None => // nothing to do
    }
  }
}


