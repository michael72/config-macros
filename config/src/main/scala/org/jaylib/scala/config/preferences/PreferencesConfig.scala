package org.jaylib.scala.config.preferences

import java.util.prefs.Preferences

class PreferencesConfig(val clz: Class[_], val defaults: Map[String, String]) {
  /** initializes the preferences.
   */
  private [this] val preferences = Preferences.userNodeForPackage(clz)

  /** Retrieves a property for a given key 
   *  @param key the property key. 
   */
  def getProperty(key: String): String = preferences.get(key, defaults.getOrElse(key, ""))
  
  /** Sets a property.
   *  @param key the property key
   *  @param value the property value 
   */
  def setProperty(key: String, value: String) {
    preferences.put(key, value)
  }
}