package subscriber

import java.time.Instant
import java.util.Base64

import akka.actor.{ActorSystem, Props}
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
  implicit val system = ActorSystem("SubClient")
  implicit val mat = ActorMaterializer()
  implicit val log = Logging(system, "SubLogger")

  /*
    Configure GC PubSub subscription
   */
  val privateKey = system.settings.config.getString("gcConfig.key").replace("\\n", "\n")
  val clientEmail = "uploadstreamdemo@uploadstream.iam.gserviceaccount.com"
  val projectId = "uploadstream"
  val config = PubSubConfig(projectId, clientEmail, privateKey)


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
    Create DeviceManager as an Actor sink
   */

  val messageReceiver = system.actorOf(DeviceManager.props(ackWith = DeviceManager.Ack))

  val messageReceiverSink = Sink.actorRefWithAck(
    messageReceiver,
    onInitMessage = DeviceManager.StreamInitialized,
    ackMessage = DeviceManager.Ack,
    onCompleteMessage = DeviceManager.StreamCompleted,
    onFailureMessage = (ex: Throwable) => DeviceManager.StreamFailure(ex)
  )

  // run subscription message through ackSink and to DeviceManager Actor
  val combinedSink = subscriptionSource.alsoTo(batchAckSink).to(messageReceiverSink)

  combinedSink.run()

  // Here we register devices in our IOT device system similar to a system in the Akka getting started guide
  // In reality, an application might have a client to register new device groups and devices, or even register these
  // based on messages from another service. We keep this outside of the app for this demo.

  try {
    io.StdIn.readLine()
  } finally {
    system.terminate()
  }
}
