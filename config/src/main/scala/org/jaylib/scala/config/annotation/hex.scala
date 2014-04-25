package org.jaylib.scala.config.annotation

import scala.annotation.StaticAnnotation
import scala.annotation.meta._/**
 * Annotation used to tell the config macro to convert integer values (Int, Short, Byte) to their hexadecimal 
 * representation. This can be used for each variable or for the whole config-class as default.
 */
@getter @setter
class hex extends StaticAnnotation