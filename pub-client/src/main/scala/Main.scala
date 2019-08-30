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


  val gcFlow: Flow[(String, String), PublishRequest, NotUsed] = Flow[(String, String)]
    .map(messageData => {
      PublishRequest(Seq(
        PubSubMessage(new String(Base64.getEncoder.encode(messageData._1.getBytes))))
      )
    })


  val bufferSize = 10
  val elementsToProcess = 5

  // newSource is a Source[PublishRequest, NotUsed]
  val (queue, newSource) = Source
    .queue[(String, String)](bufferSize, OverflowStrategy.backpressure)
    .via(gcFlow)
    .preMaterialize()


  val result = newSource.runForeach(println)

//  result onComplete {
//    case Success(ids) => println(ids)
//    case Failure(exception) => log.error("Could not process message: " + exception.getMessage)
//  }


  @tailrec
  def promptLoop(): Unit = {
    val topic = io.StdIn.readLine("Enter a Topic > ")
    val message = io.StdIn.readLine("Enter a Message > ")

    queue.offer((message, topic)).map {
//      case QueueOfferResult.Enqueued    => println(s"enqueued message: $message, topic: $topic")
      case QueueOfferResult.Dropped     => println(s"dropped message: $message, topic: $topic")
      case QueueOfferResult.Failure(ex) => println(s"Offer failed ${ex.getMessage}")
      case QueueOfferResult.QueueClosed => println("Source Queue closed")
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
