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

I will add examples and a how-to-build later. For now the Scalatest `ConfigMacrosTest` should give a good overview of what is possible with the configmacros.

See [ConfigMacrosTest.scala](https://github.com/michael72/config-macros/blob/master/macrotests/src/test/scala/org/jaylib/scala/config/macros/ConfigMacrosTest.scala)