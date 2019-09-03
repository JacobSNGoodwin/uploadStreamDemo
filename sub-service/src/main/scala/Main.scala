import java.time.Instant
import java.util.Base64

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.alpakka.googlecloud.pubsub.{AcknowledgeRequest, PubSubConfig, PublishRequest, ReceivedMessage}
import akka.stream.alpakka.googlecloud.pubsub.scaladsl.GooglePubSub
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future
import scala.concurrent.duration._

object Main extends App {
  implicit val system = ActorSystem("PublishClient")
  implicit val mat = ActorMaterializer()

  implicit val log = Logging(system, "PublishLogger")

  val privateKey = system.settings.config.getString("gcConfig.key").replace("\\n", "\n")

  val clientEmail = "uploadstreamdemo@uploadstream.iam.gserviceaccount.com"
  val projectId = "uploadstream"

  val config = PubSubConfig(projectId, clientEmail, privateKey)

  val subscriptionSource: Source[ReceivedMessage, NotUsed] =
    GooglePubSub.subscribe("subscription1", config)

  val ackSink: Sink[AcknowledgeRequest, Future[Done]] =
    GooglePubSub.acknowledge("subscription1", config)

  val decodeMessageSink: Sink[ReceivedMessage, Future[Done]] = Sink.foreach[ReceivedMessage](resp => {
    val decodedMessage = new String(Base64.getDecoder.decode(resp.message.data))
    println(decodedMessage)

    val date = resp.message.publishTime getOrElse Instant.now()
    println(date)

  })

  val batchAckSink = Flow[ReceivedMessage]
    .map(message => {
      message.ackId
    })
    .groupedWithin(1000, 1.minute)
    .map(AcknowledgeRequest.apply)
    .to(ackSink)

  val combinedSink = subscriptionSource.alsoTo(batchAckSink).to(decodeMessageSink)

  combinedSink.run()

  try {
    io.StdIn.readLine()
  } finally {
    system.terminate()
  }
}
