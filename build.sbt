name := "jobcoin-mixer"

version := "0.1"

scalaVersion := "2.12.5"

cancelable in Global := true
trapExit := false

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3" % Runtime
libraryDependencies += "com.typesafe" % "config" % "1.3.2"
libraryDependencies += "com.typesafe.play" %% "play-ahc-ws-standalone" % "2.0.0-M1"
libraryDependencies += "com.typesafe.play" %% "play-ws-standalone-json" % "2.0.0-M1"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
libraryDependencies += "org.f100ded.play" %% "play-fake-ws-standalone" % "1.1.0"
libraryDependencies += "org.mockito" %% "mockito-scala" % "1.16.15" % "test"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.4" % "test"
libraryDependencies += "org.scala-lang.modules" %% "scala-async" % "0.9.7"

val AkkaVersion = "2.6.11"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test
)
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test
)
