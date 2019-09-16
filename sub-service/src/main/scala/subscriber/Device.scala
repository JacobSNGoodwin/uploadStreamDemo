package subscriber

import akka.actor.{Actor, ActorLogging, Props}

object Device {
  def props(groupId: String, deviceId: String): Props = Props(new Device(groupId, deviceId))

  case class ReadRef(requestId: Long)
  case class RespondRef(requestId: Long, value: Option[String]) // holds path to data file
}

class Device(groupId: String, deviceId: String) extends Actor with ActorLogging {
  import Device._

  var fileRef: Option[String] = None

  override def preStart(): Unit = log.info("Device Actor {}-{} started", groupId, deviceId)
  override def postStop(): Unit = log.info("Device Actor {}-{} stopped", groupId, deviceId)

  override def receive: Receive = {
    case ReadRef(id) =>
      sender() ! RespondRef(id, fileRef)
  }
}
