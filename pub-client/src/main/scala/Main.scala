import java.util.Base64

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.googlecloud.pubsub._
import akka.stream.alpakka.googlecloud.pubsub.scaladsl.GooglePubSub
import akka.stream.javadsl.RunnableGraph
import akka.stream.scaladsl.{Flow, Sink, Source}

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

//  println(raw"$privateKey")

  val clientEmail = "uploadstreamdemo@uploadstream.iam.gserviceaccount.com"
  val projectId = "uploadstream"

  val config = PubSubConfig(projectId, clientEmail, privateKey)

  val topic = "topic1"

//  val encodedMessage = new String(Base64.getEncoder.encode("Hello Google!".getBytes))
  val message1 = new String(Base64.getEncoder.encode("Message1".getBytes))
  val message2 = new String(Base64.getEncoder.encode("Message2".getBytes))
  val message3 = new String(Base64.getEncoder.encode("Message3".getBytes))

//  val publishMessage =
//    PubSubMessage(encodedMessage)

  val publishMessages = Seq(PubSubMessage(message1), PubSubMessage(message2), PubSubMessage(message3))

  val publishRequest = PublishRequest(publishMessages)


  val source: Source[PublishRequest, NotUsed] = Source.single(publishRequest)

  val publishFlow: Flow[PublishRequest, Seq[String], NotUsed] = GooglePubSub.publish(topic, config)

  val publishedMessageIds: Future[Seq[Seq[String]]] = source.via(publishFlow).runWith(Sink.seq)



  publishedMessageIds onComplete {
    case Success(data) => for (ids <- data) println(ids)
    case Failure(t) => println("error getting published messageId", t.getMessage)
  }



  try
    StdIn.readLine()
  finally {
    system.terminate()
  }
}
