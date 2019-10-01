package subscriber

import java.util.{Base64, UUID}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.alpakka.googlecloud.pubsub.ReceivedMessage
import spray.json.JsonParser

object MessageReceiver {
  def props(ackWith: Any, deviceManagerRef: ActorRef): Props =
    Props(new MessageReceiver(ackWith: Any, deviceManagerRef: ActorRef))
  // messages for stream handling
  case object Ack
  case object StreamInitialized
  case object StreamCompleted
  final case class StreamFailure(ex: Throwable)
}
class MessageReceiver(ackWith: Any, deviceManagerRef: ActorRef) extends Actor with ActorLogging {
  import MessageReceiver._
  import CustomJsonProtocol._ // to provide implicits

  override def preStart(): Unit = log.info("Message receiver started")
  override def postStop(): Unit = log.info("Message receiver stopped")

  override def receive: Receive = {
    // handle incoming messages from GC PubSub
    case StreamInitialized =>
      log.info("Stream initialized!")
      sender() ! Ack // ack to allow the stream to proceed sending more elements
    case ReceivedMessage(id, message) =>
      val requestedDevice = JsonParser(new String(Base64.getDecoder.decode(message.data))).convertTo[DeviceRequest]
      log.info("Received {} request for deviceId: {} and groupId: {}", requestedDevice.requestType, requestedDevice.deviceId, requestedDevice.groupId)
      if (requestedDevice.requestType == "record")
        deviceManagerRef ! DeviceManager.RequestDeviceRecord(UUID.randomUUID().toString, requestedDevice.groupId, requestedDevice.deviceId)
      if (requestedDevice.requestType == "upload")
        deviceManagerRef ! DeviceManager.RequestDeviceUpload(UUID.randomUUID().toString, requestedDevice.groupId, requestedDevice.deviceId)
      sender() ! Ack // ack to allow the stream to proceed sending more elements
    case StreamCompleted =>
      log.info("Stream completed!")
    case StreamFailure(ex) =>
      log.error(ex, "Stream failed!")
    case other =>
      log.info("Received message in Message Receiver: {}", other)

  }
}
