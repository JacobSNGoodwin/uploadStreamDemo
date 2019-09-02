import java.util.Base64

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import akka.stream.alpakka.googlecloud.pubsub._
import akka.stream.alpakka.googlecloud.pubsub.scaladsl.GooglePubSub
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.annotation.tailrec
import scala.collection.immutable.Seq
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}


object Main extends App {
  implicit val system = ActorSystem("PublishClient")
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher

  implicit val log = Logging(system, "PublishLogger")

  val privateKey = system.settings.config.getString("gcConfig.key").replace("\\n", "\n")
  val clientEmail = "uploadstreamdemo@uploadstream.iam.gserviceaccount.com"
  val projectId = "uploadstream"
  val config = PubSubConfig(projectId, clientEmail, privateKey)

  println(
    """
      |Welcome to this fine and dandy Google Cloud PubSub Publisher client. Please follow the prompts!
      |""".stripMargin)


  def topicLoop(): Unit = {
    val topic = io.StdIn.readLine("Enter a Topic > ")

    // Create flow and queue based on entered topic
      val gcFlow: Flow[String, Seq[String], NotUsed] = Flow[String]
        .map(messageData => {
          PublishRequest(Seq(
            PubSubMessage(new String(Base64.getEncoder.encode(messageData.getBytes))))
          )
        })
        .via(GooglePubSub.publish(topic, config))


      val bufferSize = 10
      val elementsToProcess = 5

      // newSource is a Source[PublishRequest, NotUsed]
      val (queue, newSource) = Source
        .queue[String](bufferSize, OverflowStrategy.backpressure)
        .via(gcFlow)
        .preMaterialize()


      newSource.runForeach(println)

//      result onComplete {
//        case Success(ids) => println(ids)
//        case Failure(exception) => log.error("Could not process message: " + exception.getMessage)
//      }

      messageLoop()

    @tailrec
    def messageLoop(): Unit = {
      println(s"A message will be created and sent to '${topic}' based on the groupId and deviceId entered below.")
      val groupId = io.StdIn.readLine("Please provide the groupId  > ")
      val deviceId = io.StdIn.readLine("Please provide the deviceId > ")

      val message = s"$groupId-$deviceId"

      queue.offer(message).map {
        case QueueOfferResult.Enqueued    => println(s"enqueued message: $message, topic: $topic")
        case QueueOfferResult.Dropped     => println(s"dropped message: $message")
        case QueueOfferResult.Failure(ex) => println(s"Offer failed ${ex.getMessage}")
        case QueueOfferResult.QueueClosed => println("Source Queue closed")
      }


      val rePrompt = io.StdIn.readLine(s"Would you like to publish another message to topic '${topic}'? (y to continue, other to exit) > ")
      rePrompt match {
        case "y" | "Y" => messageLoop()
        case _ =>
          println()
      }
    }

    val rePrompt = io.StdIn.readLine("Would you like to publish to another topic? (y to continue, other to exit) > ")
    rePrompt match {
      case "y" | "Y" => topicLoop()
      case _ =>
        println("Thank you, come again!")
    }

  }


  topicLoop()
  system.terminate()
}
