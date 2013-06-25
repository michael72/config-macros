name := "SampleConfig"

version := "1.0"

scalaVersion := "2.10.1"

resolvers ++= Seq(
  "oss snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
  "oss releases" at "http://oss.sonatype.org/content/repositories/releases"
)


// remark: in sbt version 0.13 : use scalaVersion.value
libraryDependencies ++= Seq(
	"org.jaylib.scala.config" % "configbase_2.10" % "1.0.0",
	"org.jaylib.scala.config" % "configmacros_2.10" % "1.0.0")
