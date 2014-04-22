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
}
