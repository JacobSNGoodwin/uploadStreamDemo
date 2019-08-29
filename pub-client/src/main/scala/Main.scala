import java.util.Base64

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.alpakka.googlecloud.pubsub._
import akka.stream.alpakka.googlecloud.pubsub.scaladsl.GooglePubSub
import akka.stream.Attributes
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.annotation.tailrec
import scala.collection.immutable.Seq
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.io.StdIn
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object Main extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  implicit val log = Logging(system, "publishLogger")

  val privateKey = system.settings.config.getString("gcConfig.key").replace("\\n", "\n")

  val clientEmail = "uploadstreamdemo@uploadstream.iam.gserviceaccount.com"
  val projectId = "uploadstream"

  val config = PubSubConfig(projectId, clientEmail, privateKey)


  println(
    """
      |Welcome to this fine and dandy Google Cloud PubSub Publisher client. Please follow the prompts!
      |""".stripMargin)

  @tailrec
  def promptLoop(): Unit = {
    val topic = io.StdIn.readLine("Enter a Topic > ")
    val message = io.StdIn.readLine("Enter a Message > ")
    val groupId = io.StdIn.readLine("Enter a groupId > ")
    val deviceId = io.StdIn.readLine("Enter a deviceId > ")

    val publishMessage = PubSubMessage(
      new String(Base64.getEncoder.encode(message.getBytes)),
      Map("groupId" -> groupId, "deviceId" -> deviceId)
    )
    val publishRequest = PublishRequest(Seq(publishMessage))
    val source: Source[PublishRequest, NotUsed] = Source.single(publishRequest)
    val publishFlow: Flow[PublishRequest, Seq[String], NotUsed] = GooglePubSub.publish(topic, config)
    val publishedMessageIds: Future[Seq[Seq[String]]] = source.via(publishFlow).runWith(Sink.seq)
    
    try {
      val result = Await.result(publishedMessageIds, 3 second)
      println(s"Message published with the following id: ${result}")
    } catch {
      case t: Throwable => println(s"Error publishing message: ${t.getMessage}")
    }


    val rePrompt = io.StdIn.readLine("Would you like to publish another message? (y to continue, other to exit) > ")
    rePrompt match {
      case "y" | "Y" => promptLoop()
      case _ =>
        println("Thank you, come again!")
    }
  }

  promptLoop()
  system.terminate()
}
