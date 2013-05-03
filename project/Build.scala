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
  ) aggregate(configbase, configmacros /*, examples */)

  lazy val configbase: Project = Project(
    "ConfigBase",
    file("config"),
    settings = buildSettings
  )

  lazy val configmacros: Project = Project(
    "ConfigMacros",
    file("macros"),
    settings = buildSettings ++ Seq(
      libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _))
  ) dependsOn(configbase)

  /* lazy val examples: Project = Project(
    "examples",
    file("examples"),
    settings = buildSettings ++ Seq(publishArtifact := false)
  ) dependsOn(configmacros) */
}
