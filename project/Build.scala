import sbt._
import Keys._

object BuildSettings {
  val buildSettings = Defaults.defaultSettings ++ Seq (
    organization  := "org.jaylib.scala.config",
<<<<<<< HEAD
    version       := "1.1.0-SNAPSHOT",
    scalaVersion        := "2.10.4",
	//crossScalaVersions  := Seq("2.11.0-RC4", "2.10.4"),
=======
    version       := "1.0.4",
    scalaVersion  := "2.10.4",
>>>>>>> f537f2ba53ab55f0b146322f96caa48ef44c8afb
    // Sonatype OSS deployment
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    //credentials   += Credentials(Path.userHome / ".ivy2" / ".credentials"),

    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false } , 
	//useGpg := true,
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
<<<<<<< HEAD
	  //libraryDependencies += "org.scalatest" % "scalatest_2.11.0-RC4" % "2.1.3"))
	  libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.1.3" % "test"))
	  
=======
	  libraryDependencies += ("org.scalatest" % "scalatest_2.10" % "2.1.3" % "test"))
  )

>>>>>>> f537f2ba53ab55f0b146322f96caa48ef44c8afb
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
	  libraryDependencies += ("org.scalatest" % "scalatest_2.10" % "2.1.3" % "test")) ++ Seq(publishArtifact := false)
  ) dependsOn(configmacros)

  /* lazy val examples: Project = Project(
    "examples",
    file("examples"),
    settings = buildSettings ++ Seq(publishArtifact := false)
  ) dependsOn(configmacros) */
}
