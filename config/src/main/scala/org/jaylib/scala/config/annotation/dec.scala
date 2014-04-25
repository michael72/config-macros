package org.jaylib.scala.config.annotation

import scala.annotation.StaticAnnotation
import scala.annotation.meta._
/**
 * Annotation used to tell the config macro to convert integer values (Int, Short, Byte) to their decimal 
 * representation. This can be used for each variable, when hex is used for the class.
 */
@getter @setter
class dec extends StaticAnnotation