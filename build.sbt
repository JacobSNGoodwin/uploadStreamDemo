name := "uploadStreamDemo"
ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "2.12.8"

lazy val global = (project in file("."))
  .settings(
    name := "root"
  )
  .aggregate(pub, sub)

lazy val pub = (project in file("pub-client"))
  .settings(
    name := "pub",
    libraryDependencies ++= commonDependencies ++ Seq(
    )
  )
lazy val sub = (project in file("sub-service"))
  .settings(
    name := "sub",
    libraryDependencies ++= commonDependencies ++ Seq(
      dependencies.akkaTestKit,
      dependencies.scalaTest
      )
  )

lazy val dependencies = new {
  val akkaV = "2.5.25"
  val alpakkaPubSubV = "1.1.1"
  val logbackV = "1.2.3"
  val sprayJsonV = "1.3.4"
  val scalaTestV = "3.0.8"

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaV
  val akkaStreams = "com.typesafe.akka" %% "akka-stream" % akkaV
  val akkaTestKit = "com.typesafe.akka" %% "akka-testkit" % akkaV
  val alpakkaPubSub = "com.lightbend.akka" %% "akka-stream-alpakka-google-cloud-pub-sub" % alpakkaPubSubV
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaV
  val logback = "ch.qos.logback" % "logback-classic" % logbackV
  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestV
  val sprayJson = "io.spray" %%  "spray-json" % sprayJsonV
}

// add common dependencies here
lazy val commonDependencies = Seq(
  dependencies.akkaActor,
  dependencies.akkaStreams,
  dependencies.akkaSlf4j,
  dependencies.logback,
  dependencies.alpakkaPubSub,
  dependencies.sprayJson
)
