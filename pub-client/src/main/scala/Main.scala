import akka.actor.{Actor, ActorSystem}
import akka.stream.alpakka.googlecloud.pubsub.PubSubConfig
import akka.stream.ActorMaterializer

import com.typesafe.config._

import scala.io.StdIn

object Main extends App {
//  val config = ConfigFactory.load()
//  println(config.getString("gcConfig.key"))
  println("Hello from pub-client")
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  val privateKey = system.settings.config.getString("gcConfig.key") // retrieved from environment variable from application.conf
  val clientEmail = "uploadstreamdemo@uploadstream.iam.gserviceaccount.com"
  val projectId = "uploadstream"
  val apiKey = "fcd9e024865e18d50b9a52ff7ccf915d08e23381"

  println(privateKey)

  val config = PubSubConfig(projectId, clientEmail, privateKey)

  println(config)

  val topic = "topic1"
  val subscription = "subscription1"
  try
    StdIn.readLine()
  finally {
    system.terminate()
  }
}
