package subscriber

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}


object DeviceGroup {
  def props(groupId: String): Props = Props(new DeviceGroup(groupId))

  final case class RequestDeviceList(requestId: Long)
  final case class ReplyDeviceList(requestId: Long, ids: Set[String])

  // Response for case where device doesn't exist
  final case class NoSuchDevice(requestId: String)
}
class DeviceGroup(groupId: String) extends Actor with ActorLogging {
  import DeviceGroup._
  override def receive: Receive = groupReceiver(Map(), Map())

  override def preStart(): Unit = log.info("DeviceGroup {} started", groupId)

  override def postStop(): Unit = log.info("DeviceGroup {} stopped", groupId)

  def groupReceiver(deviceIdToActor: Map[String, ActorRef], actorToDeviceId: Map[ActorRef, String]): Receive = {
    // Request is this this groupId
    case trackMsg @ DeviceManager.RequestTrackDevice(`groupId`, _) =>
      deviceIdToActor.get(trackMsg.deviceId) match {
        case Some(deviceActor) =>
          log.info(s"forwarding RequestTrackDevice to ${deviceActor}")
          deviceActor.forward(trackMsg)
        case None =>
          // create device
          log.info("Creating a device actor with deviceId: {} for this groupId: {}", trackMsg.deviceId, groupId)
          val deviceActor = context.actorOf(Device.props(groupId, trackMsg.deviceId), s"device-${trackMsg.deviceId}")
          context.watch(deviceActor)
          deviceActor.forward(trackMsg)

          // update mappings and receive behavior
          val newDeviceIdToActor = deviceIdToActor + (trackMsg.deviceId -> deviceActor)
          val newActorToDeviceId = actorToDeviceId + (deviceActor -> trackMsg.deviceId)

          context.become(groupReceiver(newDeviceIdToActor, newActorToDeviceId))
      }
    case DeviceManager.RequestTrackDevice(groupId, _) =>
      // should not receive this message unless parent's list of groups goes wonky
      log.warning("Ignoring TrackDevice request for {}. This actor is responsible for {}.", groupId, this.groupId)
    case recordMsg @ DeviceManager.RequestDeviceRecord(requestId, `groupId`, _) =>
      deviceIdToActor.get(recordMsg.deviceId) match {
        case Some(deviceActor) =>
          log.info("Forwarding RequestDeviceRecord to {}", deviceActor)
          deviceActor.forward(recordMsg)
        case None =>
          log.info("The groupId '{}' has no deviceId '{}'", groupId, recordMsg.deviceId)
          sender() ! NoSuchDevice(requestId)

      }
    case uploadMsg @ DeviceManager.RequestDeviceUpload(requestId, `groupId`, _) =>
      deviceIdToActor.get(uploadMsg.deviceId) match {
        case Some(deviceActor) =>
          log.info("Forwarding RequestDeviceUpload to {}", deviceActor)
          deviceActor.forward(uploadMsg)
        case None =>
          log.info("The groupId '{}' has no deviceId '{}'", groupId, uploadMsg.deviceId)
          sender() ! NoSuchDevice(requestId)
      }

    case RequestDeviceList(requestId) =>
      sender() ! ReplyDeviceList(requestId, deviceIdToActor.keySet)
    case Terminated(deviceActor) =>
      val deviceId = actorToDeviceId(deviceActor)
      log.info("Device actor for {} has been terminated", deviceId)

      // update mappings and receive behavior
      val newDeviceIdToActor = deviceIdToActor - deviceId
      val newActorToDeviceId = actorToDeviceId - deviceActor

      context.become(groupReceiver(newDeviceIdToActor, newActorToDeviceId))
  }
}
