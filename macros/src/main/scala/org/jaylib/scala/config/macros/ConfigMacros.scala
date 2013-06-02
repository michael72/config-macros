package org.jaylib.scala.config.macros

import scala.language.experimental.macros
import scala.annotation.tailrec
import scala.collection.{ MapLike, TraversableLike }
import scala.collection.immutable.Stack
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
 *
 * config.size += 1
 * println(s"""${config.size} = ${values("size")}""") // -> 11 = 11
 * }}}
 */
object ConfigMacros {

  /**
   * Wrap macro using an own defined converter and paramSplitter.
   */
  def wrap[A, B <: TypeConversions](trt: Class[A], getter: String => String, setter: (String, String) => Unit, converter: B, paramSplitter: Splitter): A = macro wrapTraitImpl[A, B]
  /**
   * Wrap macro using an own defined converter.
   */
  def wrap[A, B <: TypeConversions](trt: Class[A], getter: String => String, setter: (String, String) => Unit, converter: B): A = macro wrapTraitImpl2[A, B]
  /**
   * Default wrap macro.
   */
  def wrap[A](trt: Class[A], getter: String => String, setter: (String, String) => Unit): A = macro wrapTraitImpl3[A]

  def wrapTraitImpl2[A: c.WeakTypeTag, B: c.WeakTypeTag](c: Context)(trt: c.Expr[A], getter: c.Expr[String => String], setter: c.Expr[(String, String) => Unit], converter: c.Expr[B]): c.Expr[A] = {
    import c.universe._
    wrapTraitImpl(c)(trt, getter, setter, converter, reify(splitter))
  }
  def wrapTraitImpl3[A: c.WeakTypeTag](c: Context)(trt: c.Expr[A], getter: c.Expr[String => String], setter: c.Expr[(String, String) => Unit]): c.Expr[A] = {
    import c.universe._
    wrapTraitImpl(c)(trt, getter, setter, reify(defaultConversions), reify(splitter))
  }

  def wrapTraitImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: Context)(trt: c.Expr[A], getter: c.Expr[String => String], setter: c.Expr[(String, String) => Unit], converter: c.Expr[B], paramSplitter: c.Expr[Splitter]): c.Expr[A] = {
    import c.universe._
    val wrapped = weakTypeOf[A]
    val convType = weakTypeOf[B]

    val isObservable = wrapped.baseClasses.exists(_.typeSignature.typeSymbol.name.decoded == splitter.shortNameOf(classOf[ObservableConfig].getName))
    val getterName = newTermName(c.fresh)
    val converterName = newTermName(c.fresh)
    val defaultSetterName = newTermName(c.fresh)
    val setterName = if (isObservable) newTermName("updateSetting") else defaultSetterName
    val splitterName = newTermName(c.fresh)

    val internalVars = new ListBuffer[Tree]
    val privateThisVal = Modifiers(Flag.PRIVATE | Flag.FINAL | Flag.LOCAL)

    val internalVals: List[c.universe.Tree] = List(
      ValDef(privateThisVal, getterName, TypeTree(), getter.tree),
      ValDef(privateThisVal, defaultSetterName, TypeTree(), setter.tree),
      ValDef(privateThisVal, converterName, TypeTree(), converter.tree),
      ValDef(privateThisVal, splitterName, TypeTree(), paramSplitter.tree)) ::: (if (isObservable) {
        List(c.parse(s"override def onSettingsChange(key: String, value: String) {${defaultSetterName}(key, value)}"))
      } else Nil)

    val unsupportedTypes = new HashSet[String]
    val createableTypes = TypeConversions.DefaultTypes ++ convType.members.filter(
      _.name.decoded.startsWith(TypeConversions.CreatorPrefix)).map(c => TypeConversions.classNameFromCreator(c.name.decoded)).toSet

    val mapAnnotations = new HashMap[String, HashSet[String]]
    def fillAnnotations {
      import scala.reflect.api.Trees
      c.enclosingUnit.body.foreach {
        item =>
        if (item.isInstanceOf[Trees#ValDefApi]) {
        	val valDef = item.asInstanceOf[ValDef]
        	mapAnnotations.getOrElseUpdate(valDef.name.decoded, HashSet()) ++= valDef.collect { case ident: IdentApi => ident.toString }
        }
      }
    }
    fillAnnotations

    def getAnnotations(varName: String) = {
      mapAnnotations.getOrElse(varName, HashSet())
    }

    def hasAnnotation(varName: String, annotation: String) = {
      getAnnotations(varName).exists(_ == annotation)
    }

    val methods = wrapped.members.filter {
      _ match {
        case m: MethodSymbol => scala.util.Try { m.accessed; true }.getOrElse(false)
        case _ => false
      }
    }.collect {
      case m: MethodSymbol if !m.isConstructor && m.accessed != NoSymbol && m.alternatives.toString.contains("List(method ") && m.isGetter => {
        // generate a warning when the trait contains a variable with a set value - this cannot be overridden!
        if (!hasAnnotation(m.name.decoded, "volatile")) {
          c.warning(m.pos, s"variable ${m.name.decoded} cannot be overridden - consider making it abstract or add the @volatile annotation to suppress this warning!")
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
        val name = m.name.decoded

        if (name.endsWith("_=")) {
          // setter method
          val sig = m.typeSignature.toString
          val rtype = sig.substring(sig.lastIndexOf(": ") + 2, sig.lastIndexOf(")"))
          val getter = name.substring(0, name.length - 2)
          val annotations = getAnnotations(getter)
          val isVolatile = annotations.contains("volatile")
          val isNotListening = annotations.contains("noListener")
          val internalVar = s"$$_${getter}_$$"
          val getterstr = "\"" + getter + "\""

          if (isObservable && !isNotListening)
            // call updateSetting(key: String, newString: String, getter: => Any, newValue: Any)
            c.parse(s"override def ${name}(n: ${rtype}) { ${setterName}(${getterstr}, ${converterName}.toString(n), ${internalVar}, n, ${isVolatile}); ${internalVar} = n};")
          else {
            if (!isVolatile)
              c.parse(s"override def ${name}(n: ${rtype}) { ${defaultSetterName}(${getterstr}, ${converterName}.toString(n)); ${internalVar} = n}");
            else
              // to use the macro for this simple functionality does not make sense, but it would in cases where some (but not all) of the values should not be saved 
              c.parse(s"override def ${name}(n: ${rtype}) { ${internalVar} = n} ");
          }
        } else {
          val isAutoConstruct = hasAnnotation(m.name.decoded, "autoConstruct")
          // getter method and initialization of internal variable
          val rtype = m.returnType.toString
          val convType = defaultConversions.getCreateString(rtype, splitter)
          val value = s"""${getterName}("${name}")""";

          val typeAccess: String = {
            val bases = m.returnType.baseClasses
            val mapLike = bases.exists(_.typeSignature.typeSymbol.name.decoded == "MapLike")
            val isSeq = !mapLike && bases.exists(_.typeSignature.typeSymbol.name.decoded == "TraversableLike")
            val isCase = !isSeq && bases.exists(_.typeSignature.typeSymbol.name.decoded == "Product") &&
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
              if (!checkChildrenOnly && shortName.length > 0 &&
                !createableTypes.contains(chkType) &&
                !createableTypes.contains(shortName) && !shortName.isEmpty &&
                !isAutoConstruct &&
                !util.Try {
                  classOf[Product].isAssignableFrom(Class.forName(chkType, true, c.getClass.getClassLoader))
                }.getOrElse(false)) {
                errorUnsupported(chkType, shortName, s"Please add the @autoConstruct annotation to ${name} or")
                false
              } else
                // I want to check _all_ children and don't break on the first non-matching child as with 'forAll' - hence this 'foldLeft' construct
                arr.foldLeft(true)((res, x) => checkTypes(x, false) && res)
            }

            if (checkTypes(rtype, isSeq || isCase || mapLike)) {
              def defaultAccess(cv: String = convType) = s"""${converterName}.tryConvert("${name}", "${cv}", ${splitterName.decoded})(${value}).asInstanceOf[${rtype}]"""
              // Handle Seq and other SeqLike types 
              if (isSeq) {
                val (listType, Seq(genericType)) = splitter.splitParamType(convType)
                if (TypeConversions.DefaultTypes.contains(listType)) // in case of a default type we don't need an extra mapping
                  defaultAccess()
                else
                  s"""{val _arr_ = ${splitterName.decoded}.apply(${value});val converter = ${converterName}.tryConvert("${name}", "${genericType}", ${splitterName.decoded})(_); _arr_.map{converter(_).asInstanceOf[${genericType.replace('$', '.')}]}.to[${listType}]}"""
              } else {
                if (isCase) {
                  /**
                   * Get the signature of a case class
                   */
                  def getSignatureOfCase(tpe: Type)(implicit stack: Stack[String] = Stack(tpe.toString)): String = util.Try {
                    tpe.member(newTermName("copy")).asInstanceOf[MethodSymbol].typeSignature match {
                      case MethodType(params, _) => params.map { innerType =>
                        val sig = innerType.typeSignature
                        val inner = sig.toString
                        if (stack.exists { outer =>
                          inner.contains(outer) && !createableTypes.contains(outer) &&
                            !createableTypes.contains(splitter.shortNameOf(outer)) && {
                              if (!isAutoConstruct) {
                                errorUnsupported(outer, splitter.shortNameOf(outer), s"Found recursive definition. Please")
                                true
                              } else false
                            }
                        }) inner
                        else getSignatureOfCase(sig)(stack.push(inner))
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

          if (typeAccess.length != 0) {
            if (wrapped.member(newTermName(name + "_$eq")) != NoSymbol) {
              val internalVar = s"$$_${name}_$$"
              internalVars += c.parse(s"private[this] var ${internalVar} : ${rtype} = ${typeAccess}")

              c.parse(s"override def ${name} : ${rtype} = ${internalVar}");
            } else {
              // in case we only have a val and no setter-method, the val is initialized with the getter 
              c.parse(s"override val ${name} : ${rtype} = ${typeAccess}");
            }
          } else {
            // unsupported type - error has already been generated - continue parsing (for other errors)
            c.parse("")
          }
        }
      }
    }.toList

    if (!unsupportedTypes.isEmpty)
      c.warning(c.enclosingPosition, "unsupported type(s) in wrapper-macro detected: " + unsupportedTypes.mkString(", "))

    val constructor = DefDef(
      Modifiers(), nme.CONSTRUCTOR, Nil, Nil :: Nil, TypeTree(),
      Block(
        Apply(
          Select(Super(This(tpnme.EMPTY), tpnme.EMPTY), nme.CONSTRUCTOR), Nil) :: Nil,
        c.literalUnit.tree))

    // construct the anonymous type that is derived from the wrapped class (wrapped.typeSymbol.name)
    val anon = c.fresh

    c.Expr(Block(
      ClassDef(Modifiers(Flag.FINAL), newTypeName(anon), Nil,
        Template(
          List(Ident(wrapped.typeSymbol.name)),
          emptyValDef,
          constructor :: (internalVals ::: internalVars.toList ::: methods))) :: Nil,
      Apply(Select(New(Ident(newTypeName(anon))), nme.CONSTRUCTOR), Nil)))
  }

  val splitter: Splitter = new DefaultSplitter
  val defaultConversions = new TypeConversions

}

