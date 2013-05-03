package org.jaylib.scala.config.convert

class ConversionException(variable: String, causedBy: Throwable) 
  extends RuntimeException(s"Error converting '${variable}': ${ConversionException.findCauseMessage(causedBy)}", causedBy)


object ConversionException {
  def findCauseMessage(t: Throwable) : String = 
    Option(t.getMessage).getOrElse(Option(t.getCause).map(findCauseMessage).getOrElse(t.getClass.getName))
}
