name := "rabbitmq-example"

version := "0.1"

scalaVersion := "2.11.6"

scalacOptions ++= Seq("-Xmax-classfile-name", "100")

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.3.11",
    "org.json4s" %% "json4s-jackson" % "3.2.11",
    "com.rabbitmq" % "amqp-client" % "3.5.3"
  )

