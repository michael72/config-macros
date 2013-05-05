package org.jaylib.scala.config.split

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

class DefaultSplitterTest extends FlatSpec with ShouldMatchers {
	val splitter = new DefaultSplitter
  
	"A splitter" should "split a simple comma-separated String to an array" in {
	  splitter("1,2,3") should be (Seq("1","2","3"))
	}
	
	it should "split a comma-separated String with brackets and an optional prefix to an array" in {
	  splitter("(1,2,3)") should be (Seq("1","2","3"))
	  splitter("[0, 1]") should be (Seq("0","1"))
	  splitter("SomePrefix (1, 2, 3)") should be (Seq("1","2","3"))
	}
	
	it should "return an empty array for empty input string-lists" in {
	  splitter("") should be (Seq[String]())
	  splitter("()") should be (Seq[String]())
	  splitter("[]") should be (Seq[String]())
	  splitter("SomePrefix[]") should be (Seq[String]())
	}
	
	it should "take nested structures as one element" in {
	  splitter("(1,(2,3),4)") should be (Seq("1", "(2,3)", "4"))
	  splitter("""(1,"2,3",,4)""") should be (Seq("1", """"2,3"""", "", "4"))
	  splitter("""(1,(2,3,"4,5"),4)""") should be (Seq("1", """(2,3,"4,5")""", "4"))
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
