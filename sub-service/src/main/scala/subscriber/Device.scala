package subscriber

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._



object Device {
  // factory method to create a new device
  def props(groupId: String, deviceId: String): Props = Props(new Device(groupId, deviceId))

  // file recording messages
  final case class RecordFile(requestId: Long)
  final case class RecordFileResponse(requestId: Long, filePath: String)
  final case class RecordFileError(reason: String)

  // file reading (respond with path) messages
  final case class ReadFiles(requestId: Long)
  final case class ReadFilesResponse(requestId: Long, filePaths: Option[Seq[String]]) // holds path to data file
  final case class ReadFilesError(reason: String)
}

class Device(groupId: String, deviceId: String) extends Actor with ActorLogging {

  import Device._
  import FileActor._

  // implicits for ask pattern
  implicit val timeout: Timeout = Timeout(5.second)
  implicit val executionContext: ExecutionContext = context.dispatcher

  override def preStart(): Unit = log.info("Device Actor {}-{} started", groupId, deviceId)

  override def postStop(): Unit = log.info("Device Actor {}-{} stopped", groupId, deviceId)

  // receiver initially has no recordings
  override def receive: Receive = deviceReceive(Seq[String]())

  // deviceReceive holds a list of current recordings
  def deviceReceive(recordings: Seq[String]): Receive = {
    case MessageReceiver.RequestTrackDevice(`groupId`, `deviceId`) =>
      log.info("Confirming device registered - groupId: {}, deviceId: {}")
      sender() ! MessageReceiver.DeviceRegistered
    case MessageReceiver.RequestTrackDevice(groupId, deviceId) =>
      log.warning(
        "Ignoring TrackDevice request for {}-{}.This actor is responsible for {}-{}.",
        groupId,
        deviceId,
        this.groupId,
        this.deviceId)
    case RecordFile(requestId) =>
      log.info("Recording Data")
      val path = s"./file-storage/$groupId-$deviceId-$requestId.txt"
      val fileActor: ActorRef = context.actorOf(Props[FileActor])
      fileActor ! Write(requestId, path, sender())
    case ReadFiles(requestId) =>
      if (recordings.length < 1) {
        log.info("Actor does not yet have available file")
        sender() ! ReadFilesResponse(requestId, None)
      } else {
        log.info("Providing list of recordings")
        sender() ! ReadFilesResponse(requestId, Some(recordings))
      }
    case FileActor.FileRecorded(requestId, filePath, originalSender) =>
      // need to handle the FileActor's future result here to change the context, but also need response
      // to the original sender. Maybe a cleaner way to do this?
      log.info("A file was recorded: {}-{}", requestId, filePath)
      originalSender ! RecordFileResponse(requestId, filePath)
      context.become(deviceReceive(recordings :+ filePath))
    case FileActor.FileRecordedFailed(originalSender) =>
      // need to handle the FileActor's future result here to change the context, but also need response
      // to the original sender. Maybe a cleaner way to do this?
      log.warning("Failed to write file")
      originalSender ! RecordFileError("FileWriteFailure")
    case _ =>
      log.warning("Actor cannot handle message")
      sender() ! ReadFilesError("UnknownMessageType")
  }
}


