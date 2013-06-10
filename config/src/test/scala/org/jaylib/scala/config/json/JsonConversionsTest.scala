package org.jaylib.scala.config.json

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import org.jaylib.scala.config.split.DefaultSplitter
import org.scalatest.GivenWhenThen
import org.jaylib.scala.config.split.Param
import org.jaylib.scala.config.convert.ConversionException

class JsonConversionsTest extends FlatSpec with ShouldMatchers with GivenWhenThen {

  val conv = new JsonConversions
  "JsonConversions" should "convert a simple case class" in {
    import JsonConversionsTest.Simple
    conv.toString(new Simple(10)) should be("{\n  x: 10\n}")
  }

  it should "convert back the simple object" in {
    import JsonConversionsTest.Simple
    conv.convertAny(classOf[Simple].getName + "(Int)", new DefaultSplitter())(Param("{ x: 42 }")) should be (Simple(42))
  }

  it should "convert a more complex example to JSON" in {
    import JsonConversionsTest._
    val tw = TestWas(11, 11, 11,
      Rec2("Marcy", List(Rec2("Jim"), Rec2("Mandy", List(Rec2("granddaughter"))), Rec2("Patrick"))),
      (12, 12.3f), "Hello!", Inner(2.0f, List(1, 2, 3)), List(Watt(1), Watt(2)), Inner(3.3f, List(4, 5, 6)))
    conv.toString(tw) should be("""{
  x: 11,
  y: 11,
  z: 11,
  rec: {
    name: "Marcy",
    children: [
      {
        name: "Jim",
        children: []
      },
      {
        name: "Mandy",
        children: [
          {
            name: "granddaughter",
            children: []
          }
        ]
      },
      {
        name: "Patrick",
        children: []
      }
    ]
  },
  tup: {
    _1: 12,
    _2: 12.3
  },
  name: "Hello!",
  inner: {
    y: 2.0,
    arr: [1, 2, 3]
  },
  arr: [
    {
      watt: 1
    },
    {
      watt: 2
    }
  ],
  inner2: {
    y: 3.3,
    arr: [4, 5, 6]
  }
}""".replaceAll("\r\n", "\n"))
  }
  
  it should "restore JSON files where the order of the members is not in the order of the configuration object" in {
     import JsonConversionsTest.Simple2
     conv.convertAny(classOf[Simple2].getName + "(Int,String)", new DefaultSplitter())(Param("""{ str: "hello", x: 42 }""")) should be (Simple2(42, "hello"))
  }
  
  it should "throw a ConversionException when not enough parameters are supplied to restore the configuration object" in {
     import JsonConversionsTest.Simple2
     intercept[ConversionException] {
    	 conv.tryConvert("Simple2", classOf[Simple2].getName + "(Int,String)", new DefaultSplitter())("""{ str: "hello" }""")
     }.getMessage should be ("Error converting 'Simple2': There are not enough names saved than are needed for re-construction - could not be restored: x")
  }

  it should "throw a ConversionException when saved names could not be associated to restore the configuration object" in {
     import JsonConversionsTest.Simple2
     intercept[ConversionException] {
    	 conv.tryConvert("Simple2", classOf[Simple2].getName + "(Int,String)", new DefaultSplitter())("""{ xx: 4, str: "hello" }""")
     }.getMessage should be ("Error converting 'Simple2': There are more names saved than are available - could not be associated: xx")
  }
}

object JsonConversionsTest {
  case class Inner(y: Float, arr: List[Int]) {
    val i = 0 // should be left out
    var yy = 1
  }
  case class Rec2(name: String, children: List[Rec2] = List())
  case class Watt(watt: Int)
  case class TestWas(x: Int, y: Int, z: Int,
                     rec: Rec2,
                     tup: (Int, Float), name: String, inner: Inner, arr: List[Watt], inner2: Inner)
  case class Simple(x: Int)
  case class Simple2(x: Int, str: String)
}