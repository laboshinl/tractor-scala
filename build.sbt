name := "tractor-scala"

version := "1.0"

scalaVersion := "2.11.8"

lazy val akkaVersion = "2.4.6"

fork in Test := true

mainClass in assembly := Some("ru.laboshinl.tractor.ApplicationMain")
assemblyJarName in assembly := "tractor.jar"
test in assembly := {} 

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  //"org.iq80.leveldb" % "leveldb" % "0.7",
  //"org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  //"com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  //"org.scalatest" %% "scalatest" % "2.2.4" % "test",
  //"commons-io" % "commons-io" % "2.4" % "test",
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.0.9",
  //"com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
  "com.github.romix.akka" %% "akka-kryo-serialization" % "0.4.1")
