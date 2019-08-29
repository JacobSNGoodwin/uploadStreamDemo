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

  // use a queue for demonstration purposes -
  val bufferSize = 10
  val elementsToProcess = 5

  // how to setup a flow dynamically in a queue?
  // why not just materialize with .run()... isn't it the same, except that you don't have a new source?
  val (queue, newSource) = Source
    .queue[(String, String)](bufferSize, OverflowStrategy.backpressure)
    .throttle(elementsToProcess, 3.second)
    .preMaterialize()

  newSource.runWith(Sink.seq)

  @tailrec
  def promptLoop(): Unit = {
    val topic = io.StdIn.readLine("Enter a Topic > ")
    val message = io.StdIn.readLine("Enter a Message > ")
//    val groupId = io.StdIn.readLine("Enter a groupId > ")
//    val deviceId = io.StdIn.readLine("Enter a deviceId > ")

    val publishMessage = PubSubMessage(
      new String(Base64.getEncoder.encode(message.getBytes)),
//      Map("groupId" -> groupId, "deviceId" -> deviceId)
    )
    val publishRequest = PublishRequest(Seq(publishMessage))

    // source will be queued as tuple
    val source: Source[PublishRequest, NotUsed] = Source.single(publishRequest)

    source
      .mapAsync(1)(req => {
      queue.offer((message, topic)).map {
        case QueueOfferResult.Enqueued    => println(s"enqueued $req")
        case QueueOfferResult.Dropped     => println(s"dropped $req")
        case QueueOfferResult.Failure(ex) => println(s"Offer failed ${ex.getMessage}")
        case QueueOfferResult.QueueClosed => println("Source Queue closed")
      }
    })
      .runWith(Sink.ignore)

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
