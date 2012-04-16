name := "PressureQueue"

version := "0.1"

scalaVersion := "2.9.1"

javacOptions += "-Xlint:unchecked"

libraryDependencies ++= Seq(
  "com.typesafe.akka" % "akka-actor" % "2.0",
  "com.typesafe.akka" % "akka-testkit" % "2.0",
  "org.scalatest" %% "scalatest" % "1.6.1" % "test"  
)
