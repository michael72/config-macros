package org.jaylib.scala.config.macros
import java.io.File
import org.jaylib.scala.config.ObservableConfig
import org.jaylib.scala.config._
import org.jaylib.scala.config.properties.PropertiesConfig
import org.jaylib.scala.config.annotation._
import org.jaylib.scala.config.convert.TypeConversions
import org.jaylib.scala.config.json.JsonFileConfig

object SampleConfig extends App {

  /**
   * Define a config that can be manipulated directly.
   *  The Observable is optional - it can be used to keep track of changed items.
   */
  trait Config extends ObservableConfig {
    @noListener
    var lastDirectory: File
    @noListener
    var lastFiles: Set[File]
    var host: String
    var port: Int
    var autoConnect: Boolean
    @noListener
    var dontUpdate: Float
    @noListener
    var dumbatz: String
  }
  
  
  val productDirectory = new File(System.getenv("APPDATA"), "JayLib")

  val defaults = Map[String, String]("lastDirectory" -> ".", "host" -> "localhost", "port" -> "8080", 
      "autoConnect" -> "false", "dontUpdate" -> "0.0f")

  /** The properties will be saved to a properties-file */
  val props = new PropertiesConfig(new File(productDirectory, "SampleConfig.properties"), defaults)
  
  // alternative version with JSON file:
  //val props = new JsonFileConfig(new File(productDirectory, "SampleConfig.json"), defaults)

  /**
   * Create an object from the Config definition where access to the variable members
   *  are redirected to the
   */
  val config = ConfigMacros.wrap(classOf[Config], props.getProperty, props.setProperty, new TypeConversions {
    def create_File(filename: String) = new File(filename)
    override def appendString(any: Any, buf: StringBuilder) = any match {
      case num: Int => buf.append(Integer.toHexString(num))
      case file: File => buf.append(file.getAbsolutePath)
      case any => super.appendString(any, buf)
    }
    override def create_Int(str: String) = Integer.parseInt(str, 16)
  })

  /* When the config extends Observable we are able to track changed properties. */
  config.bindTo(config.port) {
    (oldPort, newPort) =>
      println(s"port changed from ${oldPort} to ${newPort}")
  }
  /* Simplified version of binding where the old value is neglected. */
  config.bindToValue(config.autoConnect) {
    autoConnect => println("autoConnect is set to " + autoConnect)
  }

  /* Change same values - when run the above bindings should fire and there should be some output. */
  config.port += 1
  config.host = "127.0.0.1"
  config.autoConnect = !config.autoConnect
  config.lastFiles += new java.io.File(raw"C:\temp\test")

  props.store

}
