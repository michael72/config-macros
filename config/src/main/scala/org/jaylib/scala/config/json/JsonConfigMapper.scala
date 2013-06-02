package org.jaylib.scala.config.json

import java.io._
import scala.collection.mutable.HashMap
import scala.annotation.tailrec
import org.jaylib.scala.config.split.DefaultSplitter

/** Helper class to read and write a JSON-Configuration from a reader or to writer.
 * 
 * @param initFrom reader containing the saved data
 * @param defaults default values to use, when no data is present in the reader.
 */
class JsonConfigMapper(val initFrom: Reader, val defaults: Map[String, String]) {
  private[this] final val conversions = new JsonConversions
  private[this] final val map = {
    val buf = new StringBuilder
    val m = new HashMap[String, String]() ++ defaults
    val reader = if (initFrom.isInstanceOf[BufferedReader]) initFrom.asInstanceOf[BufferedReader] else new BufferedReader(initFrom)

    // retrieve the stored data
    @tailrec
    def readLines {
      val line = reader.readLine
      if (line != null) {
        buf.append(line).append("\n")
        readLines
      }
    }
    readLines
    new DefaultSplitter().split(buf.toString).foreach {
      setting =>
        setting.indexOf(":") match {
          case -1 => throw new RuntimeException("invalid JSON format - missing ':' in " + setting)
          case idx => m.update(setting.substring(0, idx).trim, setting.substring(idx+1).trim)
        }
        
    }
    m
  }

  /** Retrieves a property for a given key
   *  @param key the property key.
   */
  def getProperty(key: String): String = map.getOrElse(key, "")

  /** Sets a property.
   *  @param key the property key
   *  @param value the property value
   */
  def setProperty(key: String, value: String) = map.update(key, value)

  def store(writer: Writer) {
    writer.write("{\n")
    var first = true
    map.foreach {
      case (key, value) =>
        if (!first) {
          writer.write(",\n")
        }
        writer.write(key)
        writer.write(": ")
        writer.write(value)
        first = false
    }
    writer.write("\n}\n")
    writer.close
  }
}
