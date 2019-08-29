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
      dependencies.alpakkaPubSub
    )
  )
lazy val sub = (project in file("sub-service"))
  .settings(
    name := "sub",
    libraryDependencies ++= commonDependencies ++ Seq(
      )
  )

lazy val dependencies = new {
  val akkaActorV = "2.5.25"
  val alpakkaPubSubV = "1.1.1"
  val akkaSlf4jV = "2.5.25"
  val logbackV = "1.2.3"

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaActorV
  val alpakkaPubSub = "com.lightbend.akka" %% "akka-stream-alpakka-google-cloud-pub-sub" % alpakkaPubSubV
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaSlf4jV
  val logback = "ch.qos.logback" % "logback-classic" % logbackV
}

// add common dependencies here
lazy val commonDependencies = Seq(
  dependencies.akkaActor,
  dependencies.akkaSlf4j,
  dependencies.logback
)