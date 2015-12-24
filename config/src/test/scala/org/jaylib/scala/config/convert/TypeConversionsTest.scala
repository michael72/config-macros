package org.jaylib.scala.config.convert

import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.jaylib.scala.config.split.DefaultSplitter

class TypeConversionsTest extends FlatSpec with Matchers {
  val splitter = new DefaultSplitter
  val conversions = new TypeConversions
  
	"TypeConversions" should "restore a Map" in {
	  conversions.tryConvert("testMap", "Map[String,String]", 
	      splitter)("""Map("hello" -> "hallo","you" -> "du")""") should be (
	          Map ("hello" -> "hallo", "you" -> "du"))
	}	
  it should "restore another Map and convert back to the same string" in {
    val str = """Map("blue" -> "#0000ff", "red" -> "#ff0000")"""
    val map = conversions.tryConvert("testMap", "Map[String,String]", 
	      splitter)(str) 
	      map should be (Map ("blue" -> "#0000ff","red" -> "#ff0000"))
    conversions.toString(map) should be (str)
	}	
}

