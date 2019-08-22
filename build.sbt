name := "uploadStreamDemo"
ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "2.12.8"

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
      dependencies.akkaActor
    )
  )

lazy val dependencies = new {
  val akkaActorV = "2.5.6"
  val alpakkaPubSubV = "1.1.1"

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaActorV
  val alpakkaPubSub = "com.lightbend.akka" %% "akka-stream-alpakka-google-cloud-pub-sub" % alpakkaPubSubV
}

// add common dependencies here
lazy val commonDependencies = Seq(
  // add common dependencies to projects here
)
