name := "SampleConfig"

version := "1.0"

scalaVersion := "2.10.1"

// remark: in sbt version 0.13 : use scalaVersion.value
libraryDependencies ++= Seq(
	"org.jaylib.scala.config" %% "configbase" % "1.0.0",
	"org.jaylib.scala.config" %% "configmacros" % "1.0.0" % "compile")
