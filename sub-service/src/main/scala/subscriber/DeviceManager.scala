package subscriber

import java.util.Base64

import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.alpakka.googlecloud.pubsub.ReceivedMessage
import spray.json.{DefaultJsonProtocol, JsonParser}

// configure spray JSON to send JSON message with deviceId and groupId
case class DeviceTarget(deviceId: String, groupId: String)
object CustomJsonProtocol extends DefaultJsonProtocol {
  implicit val deviceFormat = jsonFormat2(DeviceTarget)
}

object DeviceManager {
  def props(ackWith: Any): Props = Props(new DeviceManager(ackWith: Any))

  // messages for stream handling
  case object Ack
  case object StreamInitialized
  case object StreamCompleted
  final case class StreamFailure(ex: Throwable)

  // Messages for Device management
  final case class RequestTrackDevice(groupId: String, deviceId: String)
  case object DeviceRegistered

}
class DeviceManager(ackWith: Any) extends Actor with ActorLogging {
  import DeviceManager._
  import CustomJsonProtocol._ // to provide implicits
  override def receive: Receive = {
    case StreamInitialized =>
      log.info("Stream initialized!")
      sender() ! Ack // ack to allow the stream to proceed sending more elements
    case ReceivedMessage(id, message) =>
      val requestedDevice = JsonParser(new String(Base64.getDecoder.decode(message.data))).convertTo[DeviceTarget]
      log.info("Received request for deviceId: {} and groupId: {}", requestedDevice.deviceId, requestedDevice.groupId)
      sender() ! Ack // ack to allow the stream to proceed sending more elements
    case StreamCompleted =>
      log.info("Stream completed!")
    case StreamFailure(ex) =>
      log.error(ex, "Stream failed!")
  }
}
