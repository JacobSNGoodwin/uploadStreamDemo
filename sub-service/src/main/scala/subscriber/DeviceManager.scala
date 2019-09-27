package subscriber

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}

object DeviceManager {
  def props(): Props = Props(new DeviceManager())

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


  override def preStart(): Unit = log.info("Device Manager started")

  override def postStop(): Unit = log.info("Device Manager stopped")

  override def receive: Receive = managerReceiver(Map(), Map())

  def managerReceiver(groupIdToActor: Map[String, ActorRef], actorToGroupId: Map[ActorRef, String]): Receive = {

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
