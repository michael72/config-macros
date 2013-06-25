addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.2.0")

addSbtPlugin(("com.typesafe.sbt" % "sbt-pgp" % "0.8").cross(CrossVersion.full)) 

useGpg := true


