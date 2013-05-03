config-macros
=============

Scala macros to provide a wrapper that maps a configuration given as trait to a getter/setter with strings. This makes it easy to access a configuration in a most direct and typesafe way and perform the saving/loading with lots of things already done automatically.

Motivation
==========

Storing and restoring properties can usually not be done in a typesafe way. Consider `java.util.Properties`. Here the properties are usually set and retrieved by String values. However what about Int or Boolean values - they have to be converted. Also try to save files or list of files. There is no generic way to do so.

Now, wouldn't it be nice to handle settings directly in the following way?

    config.port += 1
	config.host = "127.0.0.1"
	config.autoConnect = !config.autoConnect
	config.lastFiles += new java.io.File(raw"C:\temp\test")
    
Or even listen to property changes

    config.bindTo(config.port) {
      (oldPort, newPort) =>
        println(s"port changed from ${oldPort} to ${newPort}")
    }
    /* Simplified version of binding where the old value is neglected. */
    config.bindToValue(config.autoConnect) {
      autoConnect => println("autoConnect is set to " + autoConnect)
    }
    
This is the aim of config-macros: to provide an easy-to-use way to save and restore application settings.

Examples
========

For the above code to work, simply define a trait (extending [ObservableConfig](https://github.com/michael72/config-macros/blob/master/config/src/main/scala/org/jaylib/scala/config/ObservableConfig.scala) if your want to use the bind features):

    trait Config extends ObservableConfig {
      var lastDirectory: File
      var lastFiles: Set[File]
      var host: String
      var port: Int
      var autoConnect: Boolean
    }

Also you'll have to provide a getter and a setter for values. In this case, I use [PropertiesConfig](https://github.com/michael72/config-macros/blob/master/config/src/main/scala/org/jaylib/scala/config/properties/PropertiesConfig.scala) to save the settings automatically to a properties file:

    val props = new PropertiesConfig(new File(new File(System.getenv("APPDATA"), "MyProduct"), "MyApp.properties"),
      Map[String, String]("lastDirectory" -> ".", "host" -> "localhost", "port" -> "8080", "autoConnect" -> "false"))

Then we can use [ConfigMacros](https://github.com/michael72/config-macros/blob/master/macros/src/main/scala/org/jaylib/scala/config/macros/ConfigMacros.scala) to generate getters and setters for the Config-trait above. I also provide own [TypeConversions](https://github.com/michael72/config-macros/blob/master/config/src/main/scala/org/jaylib/scala/config/convert/TypeConversions.scala) for java.io.File to save the file as absolute path:

    val config = ConfigMacros.wrap(classOf[Config], props.getProperty, props.setProperty, new TypeConversions {
      def create_File(filename: String) = new File(filename)
      override def toString(any: Any) = any match {
        case file: File => file.getAbsolutePath
        case any => super.toString(any)
      }
    })

Now the config can be used as described in the code at the beginning.

I will add further examples in a sub-project and a how-to-build later. For now the Scalatest [ConfigMacrosTest](https://github.com/michael72/config-macros/blob/master/macrotests/src/test/scala/org/jaylib/scala/config/macros/ConfigMacrosTest.scala) should give a good overview of what is already possible with the configmacros.
