package org.jaylib.scala.config.preferences

import java.util.prefs.Preferences

/**
 * Creates a preferences object which can be easily used with the ConfigMacros.
 * 
 * @param preferences the preferences to use
 * @param defaults the default values 
 */
class PreferencesConfig(val preferences : Preferences, val defaults: Map[String, String]) {
  /** initializes the preferences given the class.
   *  @param clz: the class to store the user-preferences for
   *  @param defaults: default values for the preferences */
  def this(clz: Class[_], defaults : Map[String, String]) = this(Preferences.userNodeForPackage(clz), defaults) 
  
  /** Retrieves a property for a given key 
   *  @param key the property key. */
  def getProperty(key: String): String = preferences.get(key, defaults.getOrElse(key, ""))
  
  /** Sets a property.
   *  @param key the property key
   *  @param value the property value */
  def setProperty(key: String, value: String) {
    preferences.put(key, value)
  }
}