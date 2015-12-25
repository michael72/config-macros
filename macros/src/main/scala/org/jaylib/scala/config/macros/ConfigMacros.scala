package org.jaylib.scala.config.macros

import scala.language.experimental.macros
import scala.collection.{ MapLike, TraversableLike }
import scala.collection.mutable.{ HashSet, ListBuffer, HashMap }
import scala.reflect.macros.Context
import org.jaylib.scala.config._
import org.jaylib.scala.config.convert._
import org.jaylib.scala.config.split._

/**
 * Contains the main macros to implement the getters and setters of a config trait.
 *  The trait should be defined with abstract pure variables.
 *  If the trait extends `org.jaylib.scala.config.Observable` the property changes can
 *  be observed with observer-functions.
 *
 *  Example:
 * {{{
 * trait Config {
 * var size: Int
 * var checked: Boolean
 * var name: String
 * }
 * val defaults = Map(("size", "10"), ("checked", "false"), ("name", "Charly"))
 * val values = new collection.mutable.HashMap[String, String] ++ defaults
 * val config = ConfigMacros.wrap(classOf[Config], values.get(_).get, values.update)
 * // alternative using apply: 
 * // val config = ConfigMacros[Config](values.get(_).get, values.update)
 *
 * config.size += 1
 * println(s"""${config.size} = ${values("size")}""") // -> 11 = 11
 * }}}
 */
object ConfigMacros {

  /**
   * Wrap macro using an own defined converter and paramSplitter.
   */
  def wrap[A, B <: TypeConversions](trt: Class[A], getter: String => String, setter: (String, String) => Unit, converter: B,
    paramSplitter: Splitter): A = macro wrapTraitImpl[A, B]
  /**
   * Wrap macro using an own defined converter.
   */
  def wrap[A, B <: TypeConversions](trt: Class[A], getter: String => String, setter: (String, String) => Unit,
    converter: B): A = macro wrapTraitImpl2[A, B]
  /**
   * Default wrap macro.
   */
  def wrap[A](trt: Class[A], getter: String => String, setter: (String, String) => Unit): A = macro wrapTraitImpl3[A]

  def initMap[C, A, B <: TypeConversions](trtClz: C, trt: A, converter: B): Map[String, String] = macro initMapImpl[C, A, B]
  def initMap[A, B <: TypeConversions](trt: A, converter: B): Map[String, String] = macro initMapImpl1[A, B]
  def initMap[C, A](trtClz: Class[C], trtImpl: A): Map[String, String] = macro initMapImpl2[C, A]
  def initMap[A](trt: A): Map[String, String] = macro initMapImpl3[A]

  def wrapTraitImpl2[A: c.WeakTypeTag, B: c.WeakTypeTag](c: Context)(trt: c.Expr[A], getter: c.Expr[String => String], setter: c.Expr[(String, String) => Unit], converter: c.Expr[B]) : c.Expr[A]= {
    import c.universe._
    wrapTraitImpl(c)(trt, getter, setter, converter, reify(splitter))
  }
  def wrapTraitImpl3[A: c.WeakTypeTag](c: Context)(trt: c.Expr[A], getter: c.Expr[String => String], setter: c.Expr[(String, String) => Unit]) : c.Expr[A] = {
    import c.universe._
    wrapTraitImpl(c)(trt, getter, setter, reify(defaultConversions), reify(splitter))
  }

  /** Shortcut implementation which only applies getter and setter */
  def apply[A](getter: String => String, setter: (String, String) => Unit): A = macro applyImpl[A]

  def applyImpl[A: c.WeakTypeTag](c: Context)(getter: c.Expr[String => String], setter: c.Expr[(String, String) => Unit]): c.Expr[A] = {
    import c.universe._
    wrapTraitImpl3(c)(null, getter, setter)
  }
  def wrapTraitImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: Context)(trt: c.Expr[A], getter: c.Expr[String => String], setter: c.Expr[(String, String) => Unit],
    converter: c.Expr[B], paramSplitter: c.Expr[Splitter]) : c.Expr[A] = {
    import c.universe._
    val wrapped = weakTypeOf[A]
    val convType = weakTypeOf[B]
    def TermName(s: String) = newTermName(s)

    def hasBase(theType: Type, base: String): Boolean =
      theType.baseClasses.exists(_.typeSignature.typeSymbol.name.decodedName.toString == base)

    val mapAnnotations = HashMap[Symbol, Set[String]]()
    def getAnnotations(method: Symbol) = {
      mapAnnotations.getOrElseUpdate(method,
        method.annotations.map(ann => splitter.shortNameOf(ann.tpe.toString)).toSet)
    }
    def hasAnnotation(method: Symbol, annotation: String) = getAnnotations(method).contains(annotation)

    val classAnnotations = getAnnotations(wrapped.typeSymbol)
    // isDefaultConverter:
    // Some(true) => yes, it is the default converter!
    // Some(false) => no, it is overridden and the List overridden should contain all overridden methods
    // None => could not estimate if it is the default or overridden
    val (defaultConv, overridden) = checkDefaultConverter(c)(converter)
    val isDefaultConverter = if (classAnnotations.contains("simplePrimitives")) Some(true) else defaultConv

    val prefix = "macro$$";
    // the getter method that's set in the macro to get the current value is assigned to an unnamed private val
    // for better readability names with macro$$ are used.
    val getterName = TermName(prefix + "getter")
    val converterName = TermName(prefix + "converter")
    val defaultSetterName = TermName(prefix + "setter")
    val splitterName = TermName(prefix + "splitter")
    val hexConverterName = TermName(prefix + "hexConverter")

    // isObservable: is the wrapped trait derived from ObservableConfig?
    val isObservable = hasBase(wrapped, splitter.shortNameOf(classOf[ObservableConfig].getName))
    // this is the analogous setter to the getter - however 
    // depending on observable flag updateSetting is called, which calls defaultSetter in term
    val setterName = if (isObservable) TermName("updateSetting") else defaultSetterName

    val internalVars = new ListBuffer[Tree]
    var converterUsed = false
    var splitterUsed = false
    var hexConverterUsed = false
    val ownConverter = overridden.contains("appendString")

    val unsupportedTypes = new HashSet[String]
    // all types that are supported by default (the default collection types) and all that are explicitly
    // supported by the converter given by the macro parameter are collected.
    val createableTypes = TypeConversions.DefaultTypes ++ convType.members.filter(
      _.name.decodedName.toString.startsWith(TypeConversions.CreatorPrefix)).map(c => TypeConversions.classNameFromCreator(c.name.decodedName.toString)).toSet

    val methods = wrapped.members.filter {
      _ match {
        case m: MethodSymbol => scala.util.Try { m.accessed; true }.getOrElse(false)
        case _ => false
      }
    }.collect {
      case m: MethodSymbol if !m.isConstructor && m.accessed != NoSymbol && m.alternatives.toString.contains("List(method ") && m.isGetter => {
        // generate a warning when the trait contains a variable with a set value - this cannot be overridden!
        if (!hasAnnotation(m, "notSaved")) {
          c.warning(m.pos, s"variable ${m.name.decodedName.toString} cannot be overridden - consider making it abstract or add the @notSaved annotation to suppress this warning!")
        }
        c.parse("")
      }
      // override only abstract vars and values
      case m: MethodSymbol if !m.isConstructor && m.accessed == NoSymbol => {
        /**
         * Present the user a parse error when the current type cannot be handled by the wrapper.
         */
        def errorUnsupported(rtype: String, shortName: String, addMsg: String) {
          val creator = TypeConversions.creatorFromClassName(shortName)
          c.warning(m.pos, s"Unsupported type ${rtype}. ${addMsg} provide the method ${creator}(String): ${shortName} in the TypeConversions implementation and optionally override appendString(Any, StringBuilder)")
          unsupportedTypes += rtype
        }
        val name = m.name.decodedName.toString

        if (name.endsWith("_=")) {
          // setter method
          val sig = m.typeSignature.toString
          val getterName = name.substring(0, name.length - 2)
          val getterMethod = wrapped.member(TermName(getterName))
          val rtype = sig.substring(sig.lastIndexOf(": ") + 2, sig.lastIndexOf(")"))
          val hex = List("Int", "Short", "Byte").contains(rtype) && (classAnnotations.contains("hex") || hasAnnotation(getterMethod, "hex")) && !hasAnnotation(getterMethod, "dec")

          val (convClass, toString) = {
            if (primitives.contains(rtype)) {
              val methodName = s"toString_$rtype"
              if (isDefaultConverter == Some(true) ||
                (isDefaultConverter == Some(false) && !overridden.contains(methodName) && !ownConverter)) // and toString shouldn't be overridden as well
                if (hex) {
                  hexConverterUsed = true
                  ("this", hexConverterName)
                } else
                  ("java.lang." + (if (rtype == "Int") "Integer" else rtype), "toString")
              else {
                converterUsed = true
                (converterName, methodName)
              }
            } else {
              converterUsed = true
              (converterName, "toString")
            }
          }

          val annotations = getAnnotations(getterMethod) 
          val isVolatile = annotations.contains("volatile") || annotations.contains("notSaved") 
          val isNotListening = annotations.contains("noListener")
          // when debugging it's actually nicer to see the original variable names with $$ than some auto-generated indexed variable name
          val internalVar = s"$$_${getterName}_$$"
          val key = s""""$getterName""""

          val setterImpl = s"override def ${name}(n: ${rtype}) {" + (if (isObservable && !isNotListening) {
            // call updateSetting(key: String, newString: String, getter: => Any, newValue: Any)
            s"${setterName}(${key}, ${convClass}.${toString}(n), ${internalVar}, n, ${isVolatile});"
          } else {
            if (!isVolatile)
              s"${defaultSetterName}(${key}, ${convClass}.${toString}(n));"
            else
              // to use the macro for this simple functionality does not make sense, but it would in cases where some (but not all) of the values should not be saved 
              ""
          }) + s"${internalVar} = n}"
          c.parse(setterImpl)
        } else {
          val isAutoConstruct = hasAnnotation(m, "autoConstruct")
          // getter method and initialization of internal variable
          val rtype = m.returnType.toString
          val createType = defaultConversions.getCreateString(rtype, splitter)
          val value = s"""${getterName}("${name}")""";

          val typeAccess: String = {
            val mapLike = hasBase(m.returnType, "MapLike")
            val isSeq = !mapLike && hasBase(m.returnType, "TraversableLike")
            val isCaseClass = !isSeq && hasBase(m.returnType, "Product") &&
              // tuples are not handled specifically
              !rtype.contains(',')
            /**
             * Checks if the type (or its short notation) is either contained in the createable types or if it has an annotation to use
             * the default constructor anyway or if the class is a Product.
             * If not, the type is not supported and a compile error is generated.
             */
            def checkTypes(typeNames: String, checkChildrenOnly: Boolean): Boolean = {
              val (chkType, arr) = splitter.splitParamType(typeNames)
              val shortName = splitter.shortNameOf(chkType)
              if (!checkChildrenOnly &&
                !createableTypes.contains(chkType) && // checked type is in createable types -> OK
                !createableTypes.contains(shortName) && !shortName.isEmpty && // or the short version is contained -> OK
                !isAutoConstruct && // the autoConstruct annotation is set -> OK, no warning then
                !util.Try {
                  // Product -> case class -> try to auto construct without a warning
                  classOf[Product].isAssignableFrom(Class.forName(chkType, true, c.getClass.getClassLoader))
                }.getOrElse(false)) {
                errorUnsupported(chkType, shortName, s"Please add the @autoConstruct annotation to ${name} or")
                false
              } else
                // I want to check _all_ children and don't break on the first non-matching child as with 'forAll' - hence this 'foldLeft' construct
                arr.foldLeft(true)((res, x) => checkTypes(x, false) && res)
            }

            // check the types - for standard collections are case classes: only check the child items
            if (checkTypes(rtype, isSeq || isCaseClass || mapLike)) {
              def defaultAccess(cv: String = createType) = {
                val creator = TypeConversions.creatorFromClassName(cv)
                val crtype = creator.substring(TypeConversions.CreatorPrefix.length)
                val hex = List("Int", "Short", "Byte").contains(crtype) && (classAnnotations.contains("hex") || hasAnnotation(m, "hex")) && !hasAnnotation(m, "dec")
                if (createableTypes.contains(crtype)) {
                  if ((isDefaultConverter == Some(true) ||
                    (isDefaultConverter == Some(false) && !overridden.contains(creator) && !ownConverter))
                    && primitives.contains(crtype)) {
                    getPrimitiveConverter(crtype, value, hex)
                  } else {
                    converterUsed = true
                    s"${converterName}.${creator}(${value})"
                  }
                } else {
                  converterUsed = true
                  splitterUsed = true
                  s"""${converterName}.tryConvert("${name}", "${cv}", ${splitterName.decodedName.toString})(${value}).asInstanceOf[${rtype}]"""
                }
              }
              // Handle Seq and other SeqLike types 
              if (isSeq) {
                val (listType, Seq(genericType)) = splitter.splitParamType(createType)
                if (TypeConversions.DefaultTypes.contains(listType)) // in case of a default type we don't need an extra mapping
                  defaultAccess()
                else {
                  splitterUsed = true
                  converterUsed = true
                  s"""{val _arr_ = ${splitterName.decodedName.toString}.apply(${value});val converter = ${converterName}.tryConvert("${name}", "${genericType}", ${splitterName.decodedName.toString})(_); _arr_.map{converter(_).asInstanceOf[${genericType.replace('$', '.')}]}.to[${listType}]}"""
                }
              } else {
                if (isCaseClass) {
                  /**
                   * Get the recursive signature of a case class - this is not done tail-recursive, but it shouldn't
                   * go too far down the tree for the definition of the case class.
                   * Example:
                   * case class A {
                   * var x: Int
                   * }
                   * case class B {
                   * var a: A
                   * var y: Int
                   * }
                   * getSignatureOfCase(B)
                   * should deliver "B(A(Int),Int)"
                   */
                  def getSignatureOfCase(tpe: Type)(implicit signatures: List[String] = List(tpe.toString)): String = util.Try {
                    tpe.member(newTermName("copy")).asInstanceOf[MethodSymbol].typeSignature match {
                      case MethodType(params, _) => params.map { innerType =>
                        val sig = innerType.typeSignature
                        val inner = sig.toString
                        if (signatures.exists { outer =>
                          inner.contains(outer) && !createableTypes.contains(outer) &&
                            !createableTypes.contains(splitter.shortNameOf(outer)) &&
                            !isAutoConstruct && {
                              // recursive definitions have to be marked with "@autoConstruct" or they have
                              // to have an explicit creator, otherwise we get an error here.
                              // That's because recursive parsing is rather complicated and the user has to be aware, that
                              // he or she would probably have to add an own creator.
                              errorUnsupported(outer, splitter.shortNameOf(outer), s"Found recursive definition. Please")
                              true
                            }
                        }) inner
                        else getSignatureOfCase(sig)(inner :: signatures)
                      }.mkString(tpe.toString + "(", ",", ")")
                    }
                  }.getOrElse(tpe.toString)

                  // try to determine the types of the members in the case class
                  defaultAccess(getSignatureOfCase(m.returnType))
                } else
                  // handle everything else
                  defaultAccess()
              }
            } else ""
          }

          if (!typeAccess.isEmpty) {
            val (defn, valAccess) = if (wrapped.member(TermName(name + "_$eq")) != NoSymbol) {
              // the usual case: there is a setter, hence we need a variable to store the values
              val internalVar = s"$$_${name}_$$"
              internalVars += c.parse(s"private[this] var ${internalVar} : ${rtype} = ${typeAccess}")
              ("def", internalVar)
            } else {
              // in case we only have a val and no setter-method, the val is initialized with the getter 
              ("val", typeAccess)
            }
            // generate the public getter or val access here
            c.parse(s"override $defn $name : $rtype = $valAccess")
          } else {
            // unsupported type - error has already been generated - continue parsing (for other errors)
            q""
          }
        }
      }
    }.toList ::: (if (isObservable) {
      List(c.parse(s"override def onSettingsChange(key: String, value: String) {${defaultSetterName}(key, value)}"))
    } else Nil)

    if (!unsupportedTypes.isEmpty)
      c.warning(c.enclosingPosition, "unsupported type(s) in wrapper-macro detected: " + unsupportedTypes.mkString(", "))

    // flags to declare "private[this] final ... " - local = [this] is necessary, otherwise getters would be generated by the compiler
    val privateThisVal = Modifiers(Flag.PRIVATE | Flag.LOCAL | Flag.FINAL)
    // here the macro parameters of the getter, setter, converter and splitter are assigned to
    // internal variables which are later referred to as getterName, defaultSetterName, converterName and splitterName
    val internalVals: List[c.universe.Tree] = List(
      ValDef(privateThisVal, getterName, TypeTree(), getter.tree),
      ValDef(privateThisVal, defaultSetterName, TypeTree(), setter.tree)) ::: (
        if (converterUsed) List(ValDef(privateThisVal, converterName, TypeTree(), converter.tree)) else Nil) ::: (
          if (splitterUsed) List(ValDef(privateThisVal, splitterName, TypeTree(), paramSplitter.tree)) else Nil) ::: (
            if (hexConverterUsed) List(q"""private[this] def $hexConverterName(conv: Int) = String.format("0x%02x", new java.lang.Integer(conv))""") else Nil)

    c.Expr(q"new $wrapped { ..${internalVals ::: internalVars.toList ::: methods} }")
  }

  val splitter: Splitter = new DefaultSplitter
  val defaultConversions = new TypeConversions
  /**
   * All primitive types with simple conversions. Char is not used here as it is a more or less seldom used
   *  type in configurations plus it is not so easy to supply a simple conversion method for this in a macro.
   */
  val primitives = Set("Int", "Boolean", "Float", "Double", "Short", "Long", "Byte")

  def initMapImpl1[A: c.WeakTypeTag, B: c.WeakTypeTag](c: Context)(trt: c.Expr[A], converter: c.Expr[B]) = {
    import c.universe._
    initMapImpl(c)(trt, trt, converter)
  }
  def initMapImpl2[C: c.WeakTypeTag, A: c.WeakTypeTag](c: Context)(trtClz: c.Expr[C], trtImpl: c.Expr[A]) = {
    import c.universe._
    initMapImpl(c)(trtClz, trtImpl, reify(defaultConversions))
  }
  def initMapImpl3[A: c.WeakTypeTag](c: Context)(trt: c.Expr[A]) = {
    import c.universe._
    initMapImpl2(c)(trt, trt)
  }

  def initMapImpl[C: c.WeakTypeTag, A: c.WeakTypeTag, B: c.WeakTypeTag](c: Context)(trtClz: c.Expr[C], trt: c.Expr[A], converter: c.Expr[B]) : c.Expr[Map[String, String]] = {
    import c.universe._
    val wrappedClz = weakTypeOf[C]
    val prefix = "macro$map$$"
    val config = newTermName(prefix + "config")
    val configDef = q"val $config = $trt"
    val mapAnnotations = HashMap[Symbol, Set[String]]()
    def getAnnotations(method: Symbol) = {
      mapAnnotations.getOrElseUpdate(method,
        method.annotations.map(ann => splitter.shortNameOf(ann.tpe.toString)).toSet)
    }
    def hasAnnotation(method: Symbol, annotation: String) = getAnnotations(method).contains(annotation)
    val classAnnotations = getAnnotations(wrappedClz.typeSymbol)
    val (defaultConv, overridden) = checkDefaultConverter(c)(converter)
    val isDefaultConverter = if (classAnnotations.contains("simplePrimitives")) Some(true) else defaultConv

    val assignments: List[Tree] = wrappedClz.members.filter {
      _ match {
        case m: MethodSymbol => scala.util.Try { m.accessed; true }.getOrElse(false)
        case _ => false
      }
    }.collect {
      // override only abstract vars and values
      case m: MethodSymbol if !m.isConstructor && m.accessed == NoSymbol &&
        !m.name.decodedName.toString.endsWith("_=") =>
        val name = m.name.decodedName.toString
        val returnType = m.returnType.toString
        val hex = List("Int", "Short", "Byte").contains(returnType) && (classAnnotations.contains("hex") || hasAnnotation(m, "hex")) && !hasAnnotation(m, "dec")
        val termName = m.name.toTermName
        if (checkIsPrimitive(name, returnType, isDefaultConverter, overridden)) {
          val conv = if (hex) q"""{"0x" + java.lang.Integer.toHexString($config.${termName})}"""
          else returnType match {
            case "Int" => q"java.lang.Integer.toString($config.${termName})"
            case _ =>
              val tpe = c.parse(s"java.lang.${m.returnType.toString}")
              q"$tpe.toString($config.${termName})"
          }
          q"""($name, $conv)"""
        } else {
          q"""($name, $converter.toString($config.${termName}))"""
        }
    }.toList
    c.Expr(q"{$configDef; Map[String,String](..$assignments)}")
  }

  def checkDefaultConverter[B: c.WeakTypeTag](c: Context)(converter: c.Expr[B]): (Option[Boolean], List[String]) = {
    import c.universe._
    val clzName = splitter.shortNameOf(super.getClass.getName)
    val configMacrosName = if (clzName.endsWith("$")) clzName.substring(0, clzName.length - 1) else clzName
    // isDefaultConverter:
    // Some(true) => yes, it is the default converter!
    // Some(false) => no, it is overridden and the List overridden should contain all overridden methods
    // None => could not estimate if it is the default or overridden
    if (converter.tree.children.isEmpty) {
      val ovr = converter.tree.tpe.declarations.map(_.name.decodedName.toString).toList
      (if (ovr.isEmpty) None else Some(false), ovr)
    } else {
      val clzDef = converter.tree.children(0)
      clzDef match {
        case q"$mods class $tpname[..$tparams] $ctorMods(...$paramss) extends { ..$earlydefns } with ..$parents { $self => ..$stats }" =>
          if (tpname.toString == classOf[TypeConversions].getName)
            (Some(true), Nil)
          else {
            (Some(false), stats.collect {
              case q"override def $tname[..$tparams](...$paramss): $tpt = $expr" =>
                tname.toString
            }.toList)
          }

        case dflt =>
          val cmpName = configMacrosName + ".this"
          if (dflt.toString == cmpName) (Some(true), Nil) else (None, Nil)
        // TODO if it is possible to check if the current implementation of converter 
        // overwrites definitions of the original TypeConversions, overwritten could be filled here
        // but after (a lot of) experiments with symbols it doesn't seem likely at compile time
      }
    }
  }

  def checkIsPrimitive(name: String, returnType: String, isDefaultConverter: Option[Boolean], overridden: List[String]) = {
    val methodName = s"toString_$returnType"
    val ret = primitives.contains(returnType) && (isDefaultConverter == Some(true)
      || (isDefaultConverter == Some(false) && !overridden.contains(methodName) && !overridden.contains("appendString")))
    ret
  }

  def getPrimitiveConverter(crtype: String, value: String, hex: Boolean) = {
    if (hex) {
      s"""($value match { case x if x.startsWith("0x") => Integer.parseInt(x.substring(2), 16); case y => Integer.parseInt(y) }).asInstanceOf[$crtype]"""
    } else {
      crtype match {
        case "Int" => s"java.lang.Integer.parseInt(${value})"
        case _ => s"java.lang.${crtype}.parse${crtype}(${value})"
      }
    }
  }
}

