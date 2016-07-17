config-macros
=============

Scala macros to provide a wrapper for typesafe configurations that are mapped to key/value pairs as strings. The macros makes it easy to access a configuration in a direct and typesafe way and perform the conversions from and to string.

Motivation
==========

Storing and restoring properties can usually not be done in a typesafe way. Consider `java.util.Properties`. Here the properties are usually set and retrieved by String values. However what about Int or Boolean values - they have to be converted. Also try to save files or list of files. There is no generic way to do so.

Now, wouldn't it be nice to handle settings directly in the following way?

```scala
config.port += 1
config.host = "127.0.0.1"
config.autoConnect = !config.autoConnect
config.lastFiles += new java.io.File(raw"C:\temp\test")
```

Or even listen to property changes

```scala
config.bindTo(config.port) {
  (oldPort, newPort) =>
    println(s"port changed from ${oldPort} to ${newPort}")
}
/* Simplified version of binding where the old value is neglected. */
config.bindToValue(config.autoConnect) {
  autoConnect => println("autoConnect is set to " + autoConnect)
}
```

This is the aim of config-macros: to provide an easy-to-use way to save and restore application settings.

SBT configuration
=================

Currently scala 2.10 and 2.11 are supported with version 1.2.0.
add the following lines to your build.sbt:

```scala
scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
 "org.jaylib.scala.config" %% "configbase" % "1.2.0",
 "org.jaylib.scala.config" %% "configmacros" % "1.2.0" % "compile")
```


Usage
=====

To use the macros simply define a trait (extending [ObservableConfig](https://github.com/michael72/config-macros/blob/master/config/src/main/scala/org/jaylib/scala/config/ObservableConfig.scala) if you want to use the bind features):

```scala
trait Config extends ObservableConfig {
  var lastDirectory: File
  var lastFiles: Set[File]
  var host: String
  var port: Int
  var autoConnect: Boolean
}
```

The default values for the config are defined in a Map:

    val defaults = Map("lastDirectory" -> ".", "host" -> "localhost", "port" -> "8080", "autoConnect" -> "false")

To use the macro a getter and a setter for the values has to be provided. In this case, I use [PropertiesConfig](https://github.com/michael72/config-macros/blob/master/config/src/main/scala/org/jaylib/scala/config/properties/PropertiesConfig.scala) to save the settings to a properties file:
   
```scala
    val props = new PropertiesConfig(new File(new File(System.getenv("APPDATA"), "MyProduct"), "MyApp.properties"), defaults)
```

before exiting the `store` method has to be called:

```scala
props.store
```
    
Alternatively to saving the config to a properties file it could also be stored to the (user-)preferences [PreferencesConfig](https://github.com/michael72/config-macros/blob/master/config/src/main/scala/org/jaylib/scala/config/preferences/PreferencesConfig.scala):

```scala
val props = new PreferencesConfig(classOf[Config], defaults)
```
Then we can use [ConfigMacros](https://github.com/michael72/config-macros/blob/master/macros/src/main/scala/org/jaylib/scala/config/macros/ConfigMacros.scala) to generate getters and setters for the Config-trait above. I also provide own [TypeConversions](https://github.com/michael72/config-macros/blob/master/config/src/main/scala/org/jaylib/scala/config/convert/TypeConversions.scala) for java.io.File to save the file as absolute path:

```scala
val config = ConfigMacros.wrap(classOf[Config], props.getProperty, props.setProperty, new TypeConversions {
  def create_File(filename: String) = new File(filename)
  override def appendString(any: Any, buf: StringBuilder) {
    any match {
      case file: File => buf.append(file.getAbsolutePath)
      case any        => super.appendString(any, buf)
    }
  }
})
```

Now the config can be used as described in the code at the beginning.

Sample project
==============

You will find a sample project including the sbt files in [sampleconfig](https://github.com/michael72/config-macros/blob/master/sampleconfig) - the contained [SampleConfig.scala](https://github.com/michael72/config-macros/blob/master/sampleconfig/src/main/scala/org/jaylib/scala/config/macros/SampleConfig.scala) is the complete example from above. 
The Scalatest [ConfigMacrosTest](https://github.com/michael72/config-macros/blob/master/macrotests/src/test/scala/org/jaylib/scala/config/macros/ConfigMacrosTest.scala) should also give a good overview of what is already possible with the configmacros.
