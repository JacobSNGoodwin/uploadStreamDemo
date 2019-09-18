package subscriber

import java.time.Instant
import java.util.Base64

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.alpakka.googlecloud.pubsub.scaladsl.GooglePubSub
import akka.stream.alpakka.googlecloud.pubsub.{AcknowledgeRequest, PubSubConfig, ReceivedMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.{Done, NotUsed}
import spray.json.{DefaultJsonProtocol, JsonParser}

import scala.concurrent.Future
import scala.concurrent.duration._

object Main extends App {
  implicit val system = ActorSystem("PublishClient")
  implicit val mat = ActorMaterializer()
  implicit val log = Logging(system, "PublishLogger")

  /*
    Configure GC PubSub subscription
   */
  val privateKey = system.settings.config.getString("gcConfig.key").replace("\\n", "\n")
  val clientEmail = "uploadstreamdemo@uploadstream.iam.gserviceaccount.com"
  val projectId = "uploadstream"
  val config = PubSubConfig(projectId, clientEmail, privateKey)

  // configure spray JSON to send JSON message with deviceId and groupId
  case class DeviceTarget(deviceId: String, groupId: String)

  object CustomJsonProtocol extends DefaultJsonProtocol {
    implicit val deviceFormat = jsonFormat2(DeviceTarget)
  }

  import CustomJsonProtocol._ // to provide implicits

  val subscriptionSource: Source[ReceivedMessage, NotUsed] =
    GooglePubSub.subscribe("subscription1", config)

  val ackSink: Sink[AcknowledgeRequest, Future[Done]] =
    GooglePubSub.acknowledge("subscription1", config)

  /*
    Acknowledge message receipt to PubSub
   */
  val batchAckSink = Flow[ReceivedMessage]
    .map(message => {
      message.ackId
    })
    .groupedWithin(1000, 1.minute)
    .map(AcknowledgeRequest.apply)
    .to(ackSink)

  /*
    Parse PubSub JSON and connect messages to Actor System
   */
  val decodeMessageSink: Sink[ReceivedMessage, Future[Done]] = Sink.foreach[ReceivedMessage](resp => {
    val requestedDevice = JsonParser(new String(Base64.getDecoder.decode(resp.message.data))).convertTo[DeviceTarget]
    println(requestedDevice)

    val date = resp.message.publishTime getOrElse Instant.now()
    println(date)
  })

  val combinedSink = subscriptionSource.alsoTo(batchAckSink).to(decodeMessageSink)

  combinedSink.run()

  // create an actor system here - we can also send messages to create actors and add data.

  try {
    io.StdIn.readLine()
  } finally {
    system.terminate()
  }
}
