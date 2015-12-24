import sbt._
import Keys._

object BuildSettings {
  val buildSettings = Defaults.defaultSettings ++ Seq (
    organization  := "org.jaylib.scala.config",
    version       := "1.1.0",
    scalaVersion        := "2.11.7",
    crossScalaVersions := Seq("2.10.2", "2.10.3", "2.10.4", "2.11.0", "2.11.7"),
	scalacOptions ++= Seq("-deprecation"),
    // Sonatype OSS deployment
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    credentials   += Credentials(Path.userHome / ".ivy2" / ".credentials"),

    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false } , 
	pomExtra := (
      <scm>
        <url>git@github.com:michael72/config-macros.git</url>
        <connection>scm:git:git@github.com:michael72/config-macros.git</connection>
      </scm>
        <developers>
          <developer>
            <id>michael72</id>
            <name>Michael Schulte</name>
			<email>michael.schulte@gmx.org</email>
            <url>http://www.mischu.de</url>
          </developer>
        </developers>),
    licenses      := ("Apache2", new java.net.URL("http://www.apache.org/licenses/LICENSE-2.0.txt")) :: Nil,
    homepage      := Some(new java.net.URL("http://www.jaylib.org"))
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
	  libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"))
	  
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
	  libraryDependencies += ("org.scalatest" % "scalatest_2.11" % "2.2.4" % "test")) ++ Seq(publishArtifact := false)
  ) dependsOn(configmacros)

}
