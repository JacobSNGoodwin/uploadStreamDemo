import java.util.Base64

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.googlecloud.pubsub._
import akka.stream.alpakka.googlecloud.pubsub.scaladsl.GooglePubSub
import akka.stream.javadsl.RunnableGraph
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.annotation.tailrec
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object Main extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  val privateKey = system.settings.config.getString("gcConfig.key").replace("\\n", "\n")

  val clientEmail = "uploadstreamdemo@uploadstream.iam.gserviceaccount.com"
  val projectId = "uploadstream"

  val config = PubSubConfig(projectId, clientEmail, privateKey)

  def messagePrompts(): Unit = {
    println(
      """
        |Welcome to this fine and dandy Google Cloud PubSub Publisher client. Please follow the prompts!
        |""".stripMargin)

    @tailrec
    def promptLoop(): Unit = {
      val topic = io.StdIn.readLine("Please enter a valid topic to publish to > ")
      val message = io.StdIn.readLine("Please enter the message to send to the topic > ")
      val groupId = io.StdIn.readLine("Please enter the groupId you wish to receive this message > ")
      val deviceId = io.StdIn.readLine("Please enter the deviceId you wish to receive this message > ")

//      println(topic, message, groupId, deviceId)
      val publishMessage = PubSubMessage(
        new String(Base64.getEncoder.encode(message.getBytes)),
        Map("groupId" -> groupId, "deviceId" -> deviceId)
      )
      val publishRequest = PublishRequest(Seq(publishMessage))
      val source: Source[PublishRequest, NotUsed] = Source.single(publishRequest)
      val publishFlow: Flow[PublishRequest, Seq[String], NotUsed] = GooglePubSub.publish(topic, config)
      val publishedMessageIds: Future[Seq[Seq[String]]] = source.via(publishFlow).runWith(Sink.seq)

      publishedMessageIds onComplete {
        case Success(data) => for (ids <- data) println(s"Message published with the following id: ${ids(0)}")
        case Failure(t) => println(s"Error publishing message: ${t.getMessage}")
      }

      val rePrompt = io.StdIn.readLine("Would you like to publish another message? (y to continue, other to exit) > ")
      rePrompt match {
        case "y" | "Y" => promptLoop()
        case _ => system.terminate()
      }
    }

    promptLoop()
  }

  // begin prompt loop
  messagePrompts()

//  val topic = "topic1"
//
////  val encodedMessage = new String(Base64.getEncoder.encode("Hello Google!".getBytes))
//  val message1 = new String(Base64.getEncoder.encode("Message1".getBytes))
//  val data1 = Map("groupId" -> "1", "deviceId" -> "1")
//  val message2 = new String(Base64.getEncoder.encode("Message2".getBytes))
//  val data2 = Map("groupId" -> "1", "deviceId" -> "1")
//  val message3 = new String(Base64.getEncoder.encode("Message3".getBytes))
//  val data3 = Map("groupId" -> "1", "deviceId" -> "2")
//
//
//  val publishMessages = Seq(PubSubMessage(message1, data1), PubSubMessage(message2, data2), PubSubMessage(message3, data3))
//
//  val publishFlow: Flow[PublishRequest, Seq[String], NotUsed] = GooglePubSub.publish(topic, config)
//
//    // Send as flow
//    val publishRequest = PublishRequest(publishMessages)
//    val source: Source[PublishRequest, NotUsed] = Source.single(publishRequest)
//    val publishedMessageIds: Future[Seq[Seq[String]]] = source.via(publishFlow).runWith(Sink.seq)
//
//
////  // Send as batch
////  val messageSource: Source[PubSubMessage, NotUsed] = Source(publishMessages)
////  val publishedMessageIds = messageSource.groupedWithin(1000, 1.minute).map(PublishRequest.apply).via(publishFlow).runWith(Sink.seq)
//
//  publishedMessageIds onComplete {
//    case Success(data) => for (ids <- data) println(ids)
//    case Failure(t) => println("error getting published messageId", t.getMessage)
//  }
//
//  try
//    StdIn.readLine()
//  finally {
//    system.terminate()
//  }
}
