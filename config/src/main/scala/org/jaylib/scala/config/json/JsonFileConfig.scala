package org.jaylib.scala.config.json

import java.io.File
import scala.collection.mutable.HashMap
import scala.io.Source
import org.jaylib.scala.config.split.DefaultSplitter
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.StringReader
import java.io.FileReader

/** Helper class (or rather sample implementation) to store and restore Json properties
 *  to and from a file.
 */
class JsonFileConfig(val jsonFile: File, defaults: Map[String, String]) extends JsonConfigMapper( 
    if (jsonFile.exists) new FileReader(jsonFile) else new StringReader(""), defaults) {

  /** Stores the JSON file */
  def store {
    if (!jsonFile.getParentFile.exists)
      jsonFile.getParentFile.mkdirs
    store(new BufferedWriter(new FileWriter(jsonFile)))
  }
}