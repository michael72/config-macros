package org.jaylib.scala.config.split

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

class DefaultSplitterTest extends FlatSpec with ShouldMatchers {
	val splitter = new DefaultSplitter
  
	"A splitter" should "split a simple comma-separated String to an array" in {
	  val splitResult = splitter.split("1,2,3")
	  splitResult should be (Seq("1","2","3"))
	}
	
	it should "split a comma-separated String with brackets and an optional prefix to an array" in {
	  splitter.split("(1,2,3)") should be (Seq("1","2","3"))
	  splitter.split("[0, 1]") should be (Seq("0","1"))
	  splitter.split("SomePrefix (1, 2, 3)") should be (Seq("1","2","3"))
	}
	
	it should "return an empty array for empty input string-lists" in {
	  splitter.split("") should be (Seq[String]())
	  splitter.split("()") should be (Seq[String]())
	  splitter.split("[]") should be (Seq[String]())
	  splitter.split("SomePrefix[]") should be (Seq[String]())
	}
	
	it should "take nested structures as one element" in {
	  splitter.split("(1,(2,3),4)") should be (Seq("1", "(2,3)", "4"))
	  splitter.split("""(1,"2,3",,4)""") should be (Seq("1", """"2,3"""", "", "4"))
	  splitter.split("""(1,(2,3,"4,5"),4)""") should be (Seq("1", """(2,3,"4,5")""", "4"))
	  splitter.split("(1,2),(3,4)") should be (Seq("(1,2)", "(3,4)"))
	}
	
	it should "unpack prefix and array" in {
	  val (prefix,arr) = splitter.splitParamType("Prefix(1,2,3)")
	  prefix should be ("Prefix")
	  arr should be (Seq("1","2","3"))
	}
	
	it should "unpack prefix and a nested array" in {
	  val (prefix,arr) = splitter.splitParamType("InnerList(Int,List[String])")
	  prefix should be ("InnerList")
	  arr should be (Seq("Int", "List[String]"))

	  val (prefix2,arr2) = splitter.splitParamType("List[Int,InnerList(Int,List[String])]")
	  prefix2 should be ("List")
	  arr2 should be (Seq("Int", "InnerList(Int,List[String])"))
	}
	
	it should "remove the packages in the shortNameOf method" in {
	  splitter.shortNameOf("java.util.List") should be ("List")
	  splitter.shortNameOf("java.util.Map[String,java.util.List[java.io.File]]") should be ("Map[String,List[File]]")
	}
	
	
}
