package org.jaylib.scala.config.macros

import org.scalatest._
import org.scalatest.Matchers
import scala.collection.mutable.HashMap
import org.jaylib.scala.config._
import org.jaylib.scala.config.annotation._
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Leaf
import org.jaylib.scala.config.convert.TypeConversions
import org.jaylib.scala.config.split.DefaultSplitter
import org.jaylib.scala.config.split.Param
import scala.language.reflectiveCalls
import org.jaylib.scala.config.annotation.autoConstruct

import ConfigMacrosTest._

class ConfigMacrosTest extends FlatSpec with Matchers with GivenWhenThen {
  "ConfigMacros" should "work on primitive types" in {
    Given("a pure trait with vars of primitive types")
    trait Prim {
      var x: Int
      var y: Double
      var bool: Boolean
      var string: String
    }
    val map = HashMap[String, String]() ++ Map("x" -> "1", "y" -> "1.23", "bool" -> "true", "string" -> "\"eene meene miste\"")

    When("the trait is wrapped as config with the map values as initial values")
    val config = ConfigMacros[Prim](map, map.update)

    Then("the config should contain the concrete values as set in the initial map")
    config.x should be(1)
    config.y should be(1.23)
    config.bool should be(true)
    config.string should be("eene meene miste")

    And("changing the concrete values (as a whole) should result in changed values of the map")
    config.string = "1 2 3"
    map("string") should be(""""1 2 3"""")

    config.y *= 2
    map("y") should be("2.46")
  }

  it should "save and restore Strings with quotes and commas" in {
    Given("a pure trait with vars of primitive types")
    trait Strings {
      var str: String
      var list: List[String]
      var map: Map[String, List[String]]
      var mapStrings: Map[String, String]
    }
    val map = HashMap[String, String]() ++ Map(
      "str" -> """1,\"2\",3""",
      "list" -> """List("1", "2,3", 4)""",
      "map" -> """Map("" -> List(""))""",
      "mapStrings" -> """Map("hallo" -> "#hello","zweiter" -> "#second")""")

    When("the trait is wrapped as config with the map values as initial values")
    val config = ConfigMacros.wrap(classOf[Strings], map.getOrElse(_, ""), map.update)

    Then("the config should contain the correct number of parsed strings")
    config.str should be("""1,"2",3""")
    config.mapStrings should be(Map("hallo" -> "#hello", "zweiter" -> "#second"))
    config.str = """"test,1,2""""

    new TypeConversions().toString(config.str) should be(""""\"test,1,2\""""")
    map("str") should be(""""\"test,1,2\""""")
    config.list should be(List("1", "2,3", "4"))
    config.list ::= "(4,\"5,6\")"
    config.mapStrings += ("ein anderer" -> "#another")
    map("list") should be("""List("(4,\"5,6\")", "1", "2,3", "4")""")
    map("mapStrings") should be("""Map("hallo" -> "#hello", "zweiter" -> "#second", "ein anderer" -> "#another")""")
  }

  it should "work on list types" in {
    trait Lists {
      var list: List[Int]
      var seq: Seq[Double]
      var set: Set[String]
    }
    val map = HashMap[String, String]() ++ Map("list" -> "1,2,3")
    val config = ConfigMacros.wrap(classOf[Lists], map.getOrElse(_, ""), map.update)

    config.list should be(List(1, 2, 3))

    config.seq = 1.0 +: config.seq
    map("seq") should be("Vector(1.0)")
    config.seq = 2.3 +: config.seq
    map("seq") should be("Vector(2.3, 1.0)")

    config.set = Set("hey", "ya")
    map("set") should be("""Set("hey", "ya")""")

    config.list = config.list.map(_ * 2)
    map("list") should be("List(2, 4, 6)")
  }

  it should "work on tuples and case classes" in {

    trait Tup {
      var tup1: (Int, Double)
      var tup2: (String, Int)
      var cs: MyMy
    }
    val map = HashMap[String, String]() ++ Map("tup1" -> "(1,2.3)", "tup2" -> """("hello", 22)""",
      "cs" -> """MyMy(0, "yo!")""")
    val config = ConfigMacros.wrap(classOf[Tup], map.getOrElse(_, ""), map.update)

    config.tup1 should be((1, 2.3))
    config.tup2 should be(("hello", 22))
    config.cs should be(MyMy(0, "yo!"))

    config.cs = MyMy(2, "next...")
    map("cs") should be("""MyMy(2, "next...")""")

    config.tup1 = ((42, 0.123))
    map("tup1") should be("""(42, 0.123)""")

    config.tup2 = (("yeah", 33))
    map("tup2") should be("""("yeah", 33)""")
  }

  it should "work on simple generic members in case classes" in {

    Given("a pure trait with a case var containing a list")
    trait CaseWithList {
      var elem: InnerList
      var elemSimpleCase: InnerSimpleCase
      var elemInnerListOfCases: InnerListOfCases
    }
    val map = HashMap[String, String]() ++ Map(
      "elem" -> """InnerList(2, List("one", "two"))""",
      "elemSimpleCase" -> """InnerSimpleCase("Simple?", MyMy(123, "yiha"))""",
      "elemInnerListOfCases" -> """InnerListOfCases("Lots of cases", List(MyMy(1, "one case"), MyMy(2, "2nd case")))""")
    When("the trait is wrapped as config with the map values as initial values")
    val config = ConfigMacros.wrap(classOf[CaseWithList], map, map.update)
    config.elem should be(InnerList(2, List("one", "two")))
    config.elemSimpleCase should be(InnerSimpleCase("Simple?", MyMy(123, "yiha")))
    config.elemInnerListOfCases should be(InnerListOfCases("Lots of cases", List(MyMy(1, "one case"), MyMy(2, "2nd case"))))
  }

  it should "also support more complex nested generic structures" in {
    Given("a pure trait with a case var containing a complex structure with generics")
    trait CaseWithList {
      var elemInnerListCase: InnerListCaseXX
    }
    val map = HashMap[String, String]() ++ Map(
      "elemInnerListCase" -> """InnerListCaseXX("Hugo", InnerList(2, List("one", "two")))""")
    When("the trait is wrapped as config with the map values as initial values")
    val config = ConfigMacros.wrap(classOf[CaseWithList], map, map.update)
    Then("the item can be evaluated")
    config.elemInnerListCase should be(InnerListCaseXX("Hugo", InnerList(2, List("one", "two"))))
  }

  it should "work on complex nested generic structures however when the creator is provided" in {
    Given("a pure trait with a case var containing a complex structure with generics")
    trait CaseWithList {
      var elemInnerListCase: InnerListCaseXX
    }
    val map = HashMap[String, String]() ++ Map(
      "elemInnerListCase" -> """InnerListCaseXX("Hugo", InnerList(2, List("one", "two")))""")
    And("an own converter")
    val splitter = new DefaultSplitter
    val ownConverter = new TypeConversions {
      def create_InnerListCaseXX(content: String) = {
        val params = Param(content)
        val innerParams = params.children(1)
        InnerListCaseXX(create_String(params.children(0).toString),
          InnerList(innerParams.children(0).part.toInt, innerParams.children(1).children.map(c => create_String(c.part)).toList))
      }
    }
    When("the trait is wrapped as config using the individual converter")
    val config = ConfigMacros.wrap(classOf[CaseWithList], map, map.update, ownConverter)
    Then("the type can be handled")
    config.elemInnerListCase should be(InnerListCaseXX("Hugo", InnerList(2, List("one", "two"))))
  }

  it should "work on individually defined conversions" in {
    Given("A pure trait containing a class that is not supported by default - such as java.io.File")
    trait ConfigWithFile {
      var file: java.io.File
      var files: List[java.io.File]
    }
    val conversions = new TypeConversions {
      def create_File(filename: String) = new java.io.File(filename)
      override def appendString(any: Any, buf: StringBuilder) {
        any match {
          case file: java.io.File => buf.append(file.getAbsolutePath)
          case any => super.appendString(any, buf)
        }
      }
    }
    val map = HashMap[String, String]() ++ Map(
      "file" -> ".", "files" -> "")
    When("the trait is wrapped as config and an own converter is used for the file")
    val config = ConfigMacros.wrap(classOf[ConfigWithFile], map, map.update, conversions)
    Then("the file type can be handled")
    config.file should be(new java.io.File("."))

    When("adding a file with default constructor to the file list")
    config.files = new java.io.File("") :: config.files
    Then("the mapped string should contain the current dir")
    map("files") should be(s"List(${new java.io.File("").getAbsolutePath})")
  }

  it should "work on on recursive types" in {
    trait ConfigWithRecursionInner {
      @autoConstruct
      var recursive: RecursiveClz
    }
    val map = HashMap[String, String]() ++ Map(
      "recursive" -> """(fruits,(("apples", (("cox orange", ()), ("granny smith", ()))),("pears",())))""")
    When("the trait is wrapped as config")
    val config = ConfigMacros.wrap(classOf[ConfigWithRecursionInner], map, map.update)

    Then("the recursive type can be handled")
    config.recursive should be(RecursiveClz("fruits",
      List(RecursiveClz("apples", List(RecursiveClz("cox orange", Nil), RecursiveClz("granny smith", Nil))),
        RecursiveClz("pears", Nil))))
  }

  it should "work on on more complicated recursive types" in {
    trait ConfigWithRecursion {
      @autoConstruct
      var rec: ContainsRec
    }
    Given("A pure trait containing a case class with a recursive definition")
    val map = HashMap[String, String]() ++ Map(
      "rec" -> """(1,(fruits,(("apples", (("cox orange", ()), ("granny smith", ()))),("pears",()))), (1,2.0))""")
    When("the trait is wrapped as config")
    val config = ConfigMacros.wrap(classOf[ConfigWithRecursion], map, map.update)

    Then("the recursive type can be handled")
    config.rec should be(ContainsRec(1, RecursiveClz("fruits",
      List(RecursiveClz("apples", List(RecursiveClz("cox orange", Nil), RecursiveClz("granny smith", Nil))),
        RecursiveClz("pears", Nil))), (1, 2.0f)))
  }

  it should "work on types that only provide one constructor" in {
    trait ConfigWithSimpleClass {
      @autoConstruct
      var simple: Simple
      @autoConstruct
      var files: List[java.io.File] // files as well - they have a constructor with a single string argument
    }

    Given("A config trait using a type with only one constructor")
    And("the member is marked with @autoConstruct")

    val map = HashMap[String, String]() ++ Map("simple" -> "Simple(22)")

    When("the trait is wrapped as config")
    val config = ConfigMacros.wrap(classOf[ConfigWithSimpleClass], map.getOrElse(_, ""), map.update)
    Then("the simple type can be handled")
    config.simple.i should be(22)

    config.simple = new Simple(33)
    config.simple.i should be(33)
    map("simple") should be("Simple(33)")

    config.files ::= new java.io.File("""C:\temp\test""")
    config.files ::= new java.io.File(".")
    map("files") should be("""List(., C:\temp\test)""") // elements are prepended with ::=
  }
  it should "work with empty configuration strings" in {
    trait EmptyConf {
      var str: String
      var list: List[String]
    }
    val map = HashMap[String, String]()
    When("the trait is wrapped as config")
    val config = ConfigMacros.wrap(classOf[EmptyConf], map.getOrElse(_, ""), map.update)
    Then("the empty configuration should be handled")

    config.str should be("")
    config.list should be(Nil)
  }

  it should "work with brackets in strings" in {
    trait EmptyConf {
      var initDir: String
      var str: String
      var str2: String
    }
    val map = HashMap[String, String]() ++ Map("initDir" -> "\"C:\\sample\\[dir]\"", "str" -> "1(2))", "str2" -> "\"1 [2,3], 4[\"")
    When("the trait is wrapped as config")
    val config = ConfigMacros.wrap(classOf[EmptyConf], map.getOrElse(_, ""), map.update)
    Then("the string configuration should be handled")

    config.initDir should be("C:\\sample\\[dir]")
    config.str should be("1(2))")
    config.str2 should be("1 [2,3], 4[")

    config.initDir = "C:\\other\\[dir]"
    map("initDir") should be("\"C:\\other\\[dir]\"")
    config.str2 = "2 [3,4], 5["
    map("str2") should be("\"2 [3,4], 5[\"")
  }

  it should "work with more complicated case classes" in {
    trait FileAssocSettings {
      var someInt: Int
      var fileAssocs: FileAssoc
      var someBoolean: Boolean
      @notSaved
      var notSaved = ""
    }

    val conversion = new TypeConversions {
      val splitter = new DefaultSplitter
      def create_java_io_File(str: String) = new java.io.File(str.trim)
      def toString(file: java.io.File) = file.getPath.trim
    }

    val defaults = Map[String, String](
      "someInt" -> "77",
      "someBoolean" -> "true",
      "fileAssocs" -> """FileAssoc(Map("..\testsomething" -> List((5, 7), (1, 2)), "yiha" -> List((2,3)), "empty" -> List()))""",
      "notSaved" -> "<empty>")

    val map = HashMap[String, String]() ++ defaults
    val config = ConfigMacros.wrap(classOf[FileAssocSettings], map.getOrElse(_, ""), map.update, conversion)
    val cmp = FileAssoc(Map(new java.io.File("..\\testsomething") -> List((5.toByte, 7), (1.toByte, 2)), new java.io.File("yiha") -> List((2.toByte, 3)), new java.io.File("empty") -> List()))
    config.fileAssocs should be(cmp)
    val file = new java.io.File("..\\testsomething")
    val file2 = new java.io.File("a\\b\\c\\d.txt")
    val file3 = new java.io.File("simple")
    config.notSaved = "not saved"
    config.fileAssocs = config.fileAssocs.addAssoc(file, (1.toByte, 3))
    config.fileAssocs = config.fileAssocs.addAssoc(file2, (-3.toByte, 12))
    config.fileAssocs = config.fileAssocs.addAssoc(file2, (77.toByte, -44))
    config.fileAssocs = config.fileAssocs.addAssoc(file2, (22.toByte, 99))
    config.fileAssocs = config.fileAssocs.addAssoc(file2, (0.toByte, -1))
    map("fileAssocs") should be("""FileAssoc(Map(..\testsomething -> List((1, 3), (5, 7), (1, 2)), yiha -> List((2, 3)), empty -> Nil(), a\b\c\d.txt -> List((0, -1), (22, 99), (77, -44), (-3, 12))))""")
    map("notSaved") should be("<empty>")
  }
  
  it should "work with more complicated case classes and initMap" in {
    trait FileAssocSettings {
      @hex
      var someHexInt: Int
      var fileAssocs: FileAssoc
      var someBoolean: Boolean
      @notSaved
      var notSaved = ""
    }

    val conversion = new TypeConversions {
      val splitter = new DefaultSplitter
      def create_java_io_File(str: String) = new java.io.File(str.trim)
      def toString(file: java.io.File) = file.getPath.trim
    }

    val defaults = ConfigMacros.initMap(new FileAssocSettings {
      var someHexInt = 0x77
      var someBoolean = true
      var fileAssocs = FileAssoc(Map(new java.io.File("..\\testsomething") -> List((5, 7), (1, 2)), new java.io.File("yiha") -> List((2,3)), new java.io.File("empty") -> Nil))
    }, conversion)

    val map = HashMap[String, String]() ++ defaults
    
    val config = ConfigMacros.wrap(classOf[FileAssocSettings], map.getOrElse(_, ""), map.update, conversion)
    
    map("someHexInt") should be ("0x77")
    map("fileAssocs") should be("""FileAssoc(Map(..\testsomething -> List((5, 7), (1, 2)), yiha -> List((2, 3)), empty -> Nil()))""")
    map("someBoolean") should be ("true")
    
    config.someHexInt = 0x88
    map("someHexInt") should be ("0x88")
    config.fileAssocs = config.fileAssocs.addAssoc(new java.io.File("tmp"), (11.toByte, -1))
    map("fileAssocs") should be("""FileAssoc(Map(..\testsomething -> List((5, 7), (1, 2)), yiha -> List((2, 3)), empty -> Nil(), tmp -> List((11, -1))))""")
  }
}

object ConfigMacrosTest {
  // public classes used in the tests, that could not be declared inside the methods 
  // because of some reflection during instantiation
  case class MyMy(num: Int, name: String)
  case class InnerList(num: Int, list: List[String])
  case class InnerSimpleCase(name: String, mymy: MyMy)
  case class InnerListOfCases(name: String, mymys: List[MyMy])
  case class InnerListCaseXX(name: String, inner: InnerList)
  class Simple(val i: Int) {
    override def toString = s"Simple(${i})"
  }
  case class Small(i: Int)
  case class RecursiveClz(name: String, children: List[RecursiveClz])
  case class ContainsRec(before: Int, rec: RecursiveClz, tup: (Int, Float))
  case class FileAssoc(mapAssocs: Map[java.io.File, List[(Byte, Int)]]) {
    def setAssoc(file: java.io.File, assocs: List[(Byte, Int)]) =
      new FileAssoc(mapAssocs + ((file, assocs)))

    def addAssoc(file: java.io.File, assoc: (Byte, Int)) =
      if (mapAssocs.contains(file))
        new FileAssoc(mapAssocs.updated(file, assoc :: mapAssocs(file)))
      else
        new FileAssoc(mapAssocs.updated(file, assoc :: Nil))
  }

}