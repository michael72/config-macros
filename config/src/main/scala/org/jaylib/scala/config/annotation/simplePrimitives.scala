package org.jaylib.scala.config.annotation

import scala.annotation.StaticAnnotation
import scala.annotation.meta._
/**
 * Annotation used to tell the config macro to try to initialize the primitive
 * data elements with their default toString and parse methods.
 *  */
@getter @setter
class simplePrimitives extends StaticAnnotation