name := "SampleConfig"

version := "1.2"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
	"org.jaylib.scala.config" %% "configbase" % "1.2.0",
	"org.jaylib.scala.config" %% "configmacros" % "1.2.0" % "compile")
