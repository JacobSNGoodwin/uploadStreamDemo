package subscriber

import java.util.Base64

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
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
  final case class RequestGroupList(requestId: Long)
  final case class ReplyGroupList(requestId: Long, ids: Set[String])

  final case class RequestActorRef(requestId: Long, groupId: String)
  final case class ReplyActorRef(requestId: Long, groupRef: ActorRef)

  case object DeviceRegistered

}
class DeviceManager(ackWith: Any) extends Actor with ActorLogging {
  import DeviceManager._
  import CustomJsonProtocol._ // to provide implicits

  override def preStart(): Unit = log.info("Device Manager started")

  override def postStop(): Unit = log.info("Device Manager stopped")

  override def receive: Receive = managerReceiver(Map(), Map())

  def managerReceiver(groupIdToActor: Map[String, ActorRef], actorToGroupId: Map[ActorRef, String]): Receive = {
    // handle incoming messages from GC PubSub
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

    // handle device registration
    case trackMsg @ RequestTrackDevice(groupId, _) =>
      groupIdToActor.get(groupId) match {
        case Some(ref) =>
          ref.forward(trackMsg)
        case None =>
          log.info("Creating device group actor for {}", groupId)
          val groupActor = context.actorOf(DeviceGroup.props(groupId), "group-" + groupId)
          context.watch(groupActor)
          groupActor.forward(trackMsg)
          val newGroupIdToActor = groupIdToActor + (groupId -> groupActor)
          val newActorToGroupId = actorToGroupId + (groupActor -> groupId)
          context.become(managerReceiver(newGroupIdToActor, newActorToGroupId))
      }

    case RequestGroupList(requestId) =>
      sender() ! ReplyGroupList(requestId, groupIdToActor.keySet)
    case RequestActorRef(requestId, groupId) =>
      sender() ! ReplyActorRef(requestId, groupIdToActor(groupId))
    case Terminated(groupActor) =>
        val groupId = actorToGroupId(groupActor)
        log.info("Device group actor for {} has been terminated", groupId)
        val newGroupIdToActor = groupIdToActor - groupId
        val newActorToGroupId = actorToGroupId - groupActor
        context.become(managerReceiver(newGroupIdToActor, newActorToGroupId))
  }
}
