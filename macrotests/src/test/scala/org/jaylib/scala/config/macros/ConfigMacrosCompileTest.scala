package org.jaylib.scala.config.macros

import org.scalatest._
import org.scalatest.Matchers
import scala.tools.nsc._
import scala.tools.nsc.interpreter._
import java.io.StringWriter
import java.io.PrintWriter


class ConfigMacrosCompileTest extends FlatSpec with Matchers with GivenWhenThen {
  // compiler is only created once - as this is very time consuming
  // the interpreted code should be made independent from one another (i.e. by wrapping each in a unit)
  import language.reflectiveCalls
  val fixture = new {
    val settings = new Settings()
    val out = new StringWriter

    settings.classpath.value = System.getProperty("java.class.path")
    settings.embeddedDefaults[ConfigMacros.type]
    val interpreter = new IMain(settings, new PrintWriter(out))
    interpreter.interpret("import org.jaylib.scala.config._\nimport org.jaylib.scala.config.macros._\nimport scala.collection.mutable.HashMap")

    def interpret(str: String): String = {
      out.getBuffer.setLength(0)
      interpreter.interpret(str)
      out.toString
    }
  }

  "ConfigMacros Compilation" should "issue a compile warning on not directly supported types" in {
    When("interpreting a config with a File and the default conversions")
    val res = fixture.interpret("""{
    trait ConfigWithFile {
      var file: java.io.File
    }
    val map = HashMap[String, String]() ++ Map("file" -> ".")
    val config = ConfigMacros.wrap(classOf[ConfigWithFile], map, map.update)
  }""")

    Then("the interpreter should issue a warning for unsupported type file")
    res should include("warning: Unsupported type java.io.File")
    And("give a hint to add an annotation to ignore the warning and try the first constructor")
    res should include("Please add the @autoConstruct annotation to file")
    And("give a hint to provide the appropriate conversion method(s)")
    res should include("provide the method create_File(String): File in the TypeConversions implementation and optionally override appendString(Any, StringBuilder)")
  }

  it should "issue a compile warning when recursive types are used" in {
    When("interpreting a config with a recursive type")
    val res = fixture.interpret("""{
    import ConfigMacrosCompileTest.Recursive
    trait ConfigWithRecursive {
      var recursive: Recursive
    }
    val map = HashMap[String, String]() ++ Map("recursive" -> "(fruits,((apples, ((cox orange, ()), (granny smith, ()))),(pears,())))")
    val config = ConfigMacros.wrap(classOf[ConfigWithRecursive], map, map.update)
  }""")

    Then("the interpreter should issue an warning for unsupported type Recursive")
    res should include("warning: Unsupported type " + getClass.getPackage.getName + ".ConfigMacrosCompileTest.Recursive")
    And("give a hint to provide the appropriate conversion method(s)")
    res should include("provide the method create_Recursive(String): Recursive in the TypeConversions implementation and optionally override appendString(Any, StringBuilder)")
    And("do not give a hint to add an annotation to ignore the warning and try the first constructor - this cannot be handled automatically.")
    res should not include ("@autoConstruct")
  }

  it should "issue a compile warning when config variables are initialized in the trait" in {
    When("interpreting a config trait with an initialized variable")
    val res = fixture.interpret("""{
    trait ConfigInitVar {
      var x = true
    }
    val map = HashMap[String, String]()
    val config = ConfigMacros.wrap(classOf[ConfigInitVar], map, map.update)
  }""")

    Then("the interpreter should issue a warning for x")
    res should include("warning: variable x cannot be overridden")
    And("give a hint how to remove the warning")
    res should include("consider making it abstract or add the @notSaved annotation to suppress this warning!")
  }


  it should "not issue a compile warning when config @notSaved annotation is used" in {
    When("interpreting a config trait with an initialized variable with @notSaved being used")
    val res = fixture.interpret("""{
    import org.jaylib.scala.config.annotation.notSaved
    trait ConfigInitVar {
      @notSaved
      var x = true
    }
    val map = HashMap[String, String]()
    val config = ConfigMacros.wrap(classOf[ConfigInitVar], map, map.update)
  }""")
    Then("the interpreter should not issue a warning for x")
    res should not include("warning: variable x cannot be overridden")
    And("not give a hint how to remove the warning")
    res should not include("consider making it abstract or add the @notSaved annotation to suppress this warning!")
  }
  
}

object ConfigMacrosCompileTest {
  case class Recursive(name: String, children: List[Recursive])
}
