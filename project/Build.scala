import sbt._
import Keys._

object BuildSettings {
  val buildSettings = Defaults.defaultSettings ++ Seq (
    organization  := "www.jaylib.org",
    version       := "1.0.0",
    scalaVersion  := "2.10.1"
  )
}

object ConfigMacroBuild extends Build {
  import BuildSettings._

  lazy val root: Project = Project(
    "root",
    file("."),
    settings = buildSettings ++ Seq(publishArtifact := false)
  ) aggregate(configbase, configmacros, configmacrotests /*, examples */)

  lazy val configbase: Project = Project(
    "ConfigBase",
    file("config"),
    settings = buildSettings ++ Seq(
      libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-library" % _ % "test")) ++ Seq(
      libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _ % "test")) ++ Seq(
	  libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-compiler" % _ % "test"))  ++ Seq(
	  libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-actors" % _ % "test"))  ++ Seq(
	  libraryDependencies += ("org.scalatest" % "scalatest_2.10" % "1.9.1" % "test"))
  )

  lazy val configmacros: Project = Project(
    "ConfigMacros",
    file("macros"),

    settings = buildSettings ++ Seq(
      libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _)) 
  ) dependsOn(configbase)

 lazy val configmacrotests: Project = Project(
    "ConfigMacroTests",
    file("macrotests"),
    settings = buildSettings ++ Seq(
      libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _)) ++ Seq(
	  libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-compiler" % _ % "test"))  ++ Seq(
	  libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-actors" % _ % "test"))  ++ Seq(
	  libraryDependencies += ("org.scalatest" % "scalatest_2.10" % "1.9.1" % "test")) ++ Seq(publishArtifact := false)
  ) dependsOn(configmacros)

  /* lazy val examples: Project = Project(
    "examples",
    file("examples"),
    settings = buildSettings ++ Seq(publishArtifact := false)
  ) dependsOn(configmacros) */
}
