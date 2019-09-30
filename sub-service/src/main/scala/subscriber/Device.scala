package subscriber

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._



object Device {
  // factory method to create a new device
  def props(groupId: String, deviceId: String): Props = Props(new Device(groupId, deviceId))

  // file recording messages
  final case class RecordFileResponse(requestId: String, filePath: String)
  final case class RecordFileError(reason: String)

  // file reading (respond with path) messages
  // so far only used in tests
  final case class ReadFiles(requestId: String)
  final case class ReadFilesResponse(requestId: String, filePaths: Option[Set[String]]) // holds path to data file
  final case class ReadFilesError(reason: String)

  // file uploading/streaming to GC
  final case class UploadFiles(requestId: String)
  final case class UploadFilesResponse(requestId: String)
  final case class UploadFilesError(reason: String)
}

class Device(groupId: String, deviceId: String) extends Actor with ActorLogging {
  import Device._

  // implicits for ask pattern
  implicit val timeout: Timeout = Timeout(5.second)
  implicit val executionContext: ExecutionContext = context.dispatcher

  override def preStart(): Unit = log.info("Device Actor {}-{} started", groupId, deviceId)

  override def postStop(): Unit = log.info("Device Actor {}-{} stopped", groupId, deviceId)

  // each Device will use a single file actor for this demo
  val fileActor: ActorRef = context.actorOf(Props[FileActor])

  // receiver initially has no recordings
  override def receive: Receive = deviceReceive(Set[String]())

  // deviceReceive holds a list of current recordings
  def deviceReceive(recordings: Set[String]): Receive = {
    // DEVICE TRACKING MESSAGES
    case DeviceManager.RequestTrackDevice(`groupId`, `deviceId`) =>
      log.info("Confirming device registered - groupId: {}, deviceId: {}", groupId, deviceId)
      sender() ! DeviceManager.DeviceRegistered
    case DeviceManager.RequestTrackDevice(groupId, deviceId) =>
      log.warning(
        "Ignoring TrackDevice request for {}-{}.This actor is responsible for {}-{}.",
        groupId,
        deviceId,
        this.groupId,
        this.deviceId)
    // CREATE AND LISTING FILES
    case DeviceManager.RequestDeviceRecord(requestId, `groupId`,`deviceId`) =>
      log.info("Device received request to record data for request id '{}'", requestId)
      val path = s"./file-storage/$groupId-$deviceId-$requestId.txt"
      fileActor ! FileActor.Write(requestId, path, sender())
    case ReadFiles(requestId) =>
      if (recordings.isEmpty) {
        log.info("Actor does not yet have available file")
        sender() ! ReadFilesResponse(requestId, None)
      } else {
        log.info("Providing list of recordings")
        sender() ! ReadFilesResponse(requestId, Some(recordings))
        sender() ! ReadFilesResponse(requestId, Some(recordings))
      }
    case FileActor.FileRecorded(requestId, filePath, originalSender) =>
      // need to handle the FileActor's future result here to change the context, but also need response
      // to the original sender. Maybe a cleaner way to do this?
      log.info("A file was recorded: {}-{}", requestId, filePath)
      originalSender ! RecordFileResponse(requestId, filePath)
      context.become(deviceReceive(recordings + filePath))
    case FileActor.FileRecordedFailed(originalSender) =>
      // need to handle the FileActor's future result here to change the context, but also need response
      // to the original sender. Maybe a cleaner way to do this?
      log.warning("Failed to write file")
      originalSender ! RecordFileError("FileWriteFailure")
    // UPLOADING FILES
    case DeviceManager.RequestDeviceUpload(requestId, `groupId`, `deviceId`) =>
      if (recordings.isEmpty) {
        log.info("Device '{}' has no files to upload", deviceId)
      } else {
        for (filePath <- recordings) {
          fileActor ! FileActor.FileUpload(requestId, filePath, sender())
        }
      }
    case FileActor.FileUploadResponse(requestId, filePath, originalSender, _) =>
      log.info("File at path '{}' in request '{}' has been successfully uploaded", filePath, requestId)
      originalSender ! UploadFilesResponse(requestId)
      new File(filePath).delete()
      context.become(deviceReceive(recordings - filePath))
    case _ =>
      log.warning("Actor cannot handle message")
      sender() ! ReadFilesError("UnknownMessageType")
  }
}


