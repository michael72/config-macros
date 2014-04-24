package org.jaylib.scala.config.convert

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import org.jaylib.scala.config.split.DefaultSplitter

class TypeConversionsTest extends FlatSpec with ShouldMatchers {
  val splitter = new DefaultSplitter
  val conversions = new TypeConversions
  
	"TypeConversions" should "restore a Map" in {
	  conversions.tryConvert("testMap", "Map[String,String]", 
	      splitter)("""Map("hello" -> "hallo","you" -> "du")""") should be (
	          Map ("hello" -> "hallo", "you" -> "du"))
	}	
}

