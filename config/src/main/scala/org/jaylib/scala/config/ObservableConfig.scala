package org.jaylib.scala.config

import scala.collection.mutable.{ HashMap, MultiMap, Set }

/**
 * Configurations that extend ObservableConfig provide bindings to their 
 * properties. These properties can be watched for changes.
 * ConfigMacros.wrap creates implements onSettingsChange automatically and generates
 * a call to updateSetting in each property setter method.
 * 
 * Example:
 {{{
   trait Config extends ObservableConfig {
    var host: String
    var port: Int
    var autoConnect: Boolean
  }
  /**
   * Create an object from the Config definition where access to the variable members
   *  are redirected to the
   */
  val config = ConfigMacros.wrap(classOf[Config], myGetter, mySetter)

  /* When the config extends ObservableConfig we are able to track changed properties. */
  config.bindTo(config.port) {
    (oldPort, newPort) =>
      println(s"port changed from ${oldPort} to ${newPort}")
  }
  /* Simplified version of binding where the old value is neglected. */
  config.bindToValue(config.autoConnect) {
    autoConnect => println("autoConnect is set to " + autoConnect)
  }
}}}
 */
trait ObservableConfig {
  private[this] case class Binding(onChange: (Any, Any) => Unit)
  private[this] val bindings = new HashMap[Any, Set[Binding]] with MultiMap[Any, Binding]

  /**
   * Bind a listener to a property of the config.
   * 
   * @paramT T type of the property 
   * @param property the property (i.e. the getter of the var) to watch for changes
   * @param listener taking 2 T arguments: (oldValue, newValue) for the old and new value of the
   * watched property. 
   */
  final def bindTo[T](property: T)(listener: (T, T) => Unit) {
    bindings.addBinding(property, Binding((oldVal, newVal) => listener(oldVal.asInstanceOf[T], newVal.asInstanceOf[T])))
  }
  
  
  /**
   * Simplified version of binding a listener to a property of the config.
   * The listener is only informed about a new value with getting the old value with it.
   * 
   * @paramT T type of the property 
   * @param property the property (i.e. the getter of the var) to watch for changes
   * @param listener taking one T argument: (newValue) for the new value of the
   * watched property. 
   */
  final def bindToValue[T](property: T)(listener: T => Unit) {
    bindings.addBinding(property, Binding((_, newVal) => listener(newVal.asInstanceOf[T])))
  }
  
  /**
   * Remove all bindings from a property.
   */
  final def removeBindings(property: Any) {
    bindings.remove(property)
  }

  /**
   * This method will be implemented automatically by ConfigMacros#wrap().
   * It calls the real setter method provided in the macro call.
   */
  def onSettingsChange(key: String, newValue: String)

  /**
   * This method will be called automatically when a setter in the config is called,
   * when the config was wrapped using ConfigMacros#wrap.
   */
  protected[this] final def updateSetting(key: String, newString: String, property: Any, newValue: Any, isVolatile: Boolean) {

    bindings.get(property) match {
      case Some(listenerSet) =>
        val oldValue = property
        if (oldValue != newValue)
          listenerSet.foreach(_.onChange(oldValue, newValue)) // call the listener(s)
      case None => // there is no listener
    }
    if (!isVolatile) onSettingsChange(key, newString)
  }

}