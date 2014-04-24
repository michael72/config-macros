package org.jaylib.scala.config.split

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

class ParamTest extends FlatSpec with ShouldMatchers {
  
	"Param" should "convert parameters correctly back to string" in {
	  val str = """Map("..\testsomething" -> List((5,7),(1,2)),"yiha" -> List((2,3)),"empty" -> Nil)"""
	  val param = Param(str)
	  val toStr = param.toString
	  toStr should be (str)
	}	

	it should "convert a simple Map[String,String] back to string" in {
	  val str = """Map("hello" -> "hallo","zweiter" -> "second","number one" -> "#1")"""
	  val param = Param(str)
	  val toStr = param.toString
	  toStr should be (str)
	}
	
	it should "unpack a simple string" in {
	  Param.unpackString(""""simple"""") should be ("simple")
	}
	
	it should "not unpack a string that contains two string elements" in {
	  val str = """"1" "2""""
	  val unpacked = Param.unpackString(str)
	  unpacked should be (str)
	}
}
