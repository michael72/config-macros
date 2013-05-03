package org.jaylib.scala.config.annotation

import scala.annotation.StaticAnnotation

/**
 * Annotation used to tell the config macro to try to initialize the data
 * with automatically. This can be safely used for case classes and
 * as far as it can be determined automatically this annotation should not be
 * necessary. However in the context of the macro it is sometimes not possible
 * to determine automatically if the given type can be constructed in the default
 * way and issues a warning. Hence this annotation to prevent the warning.
 */
class autoConstruct extends StaticAnnotation