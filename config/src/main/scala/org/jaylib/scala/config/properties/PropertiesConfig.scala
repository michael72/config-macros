package org.jaylib.scala.config.properties

import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.Properties

/**
 * Helper class to save settings in a property file.
 * 
 * @param propertiesFile location of the ".properties"-File
 * @param defaults the default values to use as initial values, when the properties file is still empty.
 */
class PropertiesConfig(val propertiesFile: File, val defaults: Map[String, String]) {
  /** creates and initializes the properties.
   */
  private [this] val properties = new Properties {
    if (!propertiesFile.exists) {
      propertiesFile.getParentFile.mkdirs
      propertiesFile.createNewFile
    }
    load(new FileReader(propertiesFile))
  }

  /** Retrieves a property for a given key 
   *  @param key the property key. 
   */
  def getProperty(key: String): String = {
    properties.getProperty(key) match {
      case null => // no key found -> use defaults
        defaults.get(key) match {
          case None => "" // not even in defaults -> empty string (works for Iterable types and Strings)
          case Some(setting) => setting
        }
      case setting => setting
    }
  }
  
  /** Sets a property.
   *  @param key the property key
   *  @param value the property value 
   */
  def setProperty(key: String, value: String) {
    properties.setProperty(key, value)
  }

  /** Stores the properties file */
  def store {
    properties.store(new FileWriter(propertiesFile), "")
  }

}