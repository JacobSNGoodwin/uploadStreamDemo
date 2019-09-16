package subscriber

import java.nio.file.Paths

import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString

import scala.concurrent.Future
import scala.util.Random

object Device {
  // factory method to create a new device
  def props(groupId: String, deviceId: String): Props = Props(new Device(groupId, deviceId))

  // messages to start recording data and confirm it is recording
  final case class RecordData(requestId: Long)
  final case class FileRecorded(requestId: Long)

  final case class ReadError(reason: String)
  final case class ReadFileRef(requestId: Long)
  final case class RespondRef(requestId: Long, value: Option[String]) // holds path to data file
}

class Device(groupId: String, deviceId: String) extends Actor with ActorLogging {
  import Device._

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  override def preStart(): Unit = log.info("Device Actor {}-{} started", groupId, deviceId)
  override def postStop(): Unit = log.info("Device Actor {}-{} stopped", groupId, deviceId)

  override def receive: Receive = awaitingRecordFile

  def awaitingRecordFile: Receive = {
    case ReadFileRef(id) =>
      log.info("Actor does not yet have available file")
      sender() ! RespondRef(id, None)
    case RecordData(id) =>
      log.info("Recording Data")
      writeDeviceData(s"data-$groupId-$deviceId-$id.txt")
      context.become(awaitingFileRecorded)
    case _ =>
      log.warning("Actor cannot handle message")
      sender() ! ReadError("UnknownMessage")
  }

  def awaitingFileRecorded: Receive = {
    case _ => log.info("Awaiting file upload")
  }

  def writeDeviceData(fileName: String): Future[IOResult] = {
    // write file asynchronously with Akka Streams with source of 10 256 kB chunks
    val file = Paths.get(fileName)
    val text = Source(1 to 10).map(_ => Random.alphanumeric.take(256 * 1024).mkString) // source of 256 kB chunks

    text.map(t => ByteString(t)).runWith(FileIO.toPath(file))
  }
}
