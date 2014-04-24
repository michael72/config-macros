package org.jaylib.scala.config.annotation

import scala.annotation.StaticAnnotation
import scala.annotation.meta._
/**
 * Annotation to tell the config wrapper macro not to generate code to
 * inform any listeners for value changes despite the config extending ObservableConfig. 
 * This generates a slightly more efficient code that does not call the update
 * function in the setter.
 */
@getter @setter
class noListener extends StaticAnnotation