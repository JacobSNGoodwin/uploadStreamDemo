package subscriber

import java.nio.file.Paths

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.{ask, pipe}
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.{ByteString, Timeout}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success, Try}


object Device {
  // factory method to create a new device
  def props(groupId: String, deviceId: String): Props = Props(new Device(groupId, deviceId))

  // file recording messages
  final case class RecordFile(requestId: Long)
  final case class RecordFileResponse(requestId: Long)
  final case class RecordFileError(reason: String)

  // file reading (respond with path) messages
  final case class ReadFile(requestId: Long)
  final case class ReadFileResponse(requestId: Long, filePath: Option[String]) // holds path to data file
  final case class ReadFileError(reason: String)
}

class Device(groupId: String, deviceId: String) extends Actor with ActorLogging {
  import Device._
  import FileActor._

  // implicits for ask pattern
  implicit val timeout: Timeout = Timeout(5.second)
  implicit val executionContext: ExecutionContext = context.dispatcher

  override def preStart(): Unit = log.info("Device Actor {}-{} started", groupId, deviceId)
  override def postStop(): Unit = log.info("Device Actor {}-{} stopped", groupId, deviceId)

  override def receive: Receive = awaitingRecordFile

  protected val fileActor: ActorRef = context.actorOf(Props[FileActor])

  // state waiting for message to record a file (in this demo, saves a dummy file to disk)
  def awaitingRecordFile: Receive = {
    case ReadFile(requestId) =>
      log.info("Actor does not yet have available file")
      sender() ! ReadFileResponse(requestId, None)
    case RecordFile(requestId) =>
      log.info("Recording Data")
      val path = s"./file-storage/$groupId-$deviceId-$requestId.txt"
      handleFileWrite(path, requestId)
      context.become(awaitUploadFile(path))
    case _ =>
      log.warning("Actor cannot handle message")
      sender() ! ReadFileError("UnknownMessageType")
  }

  def awaitUploadFile(filePath: String): Receive = {
    case ReadFile(requestId) =>
      log.info(s"Reading File Ref for requestId: ${requestId}")
      sender() ! ReadFileResponse(requestId, Some(filePath))
  }

  def handleFileWrite(filePath: String, requestId: Long): Unit = {
    // ask file actor to file write
    val future = fileActor ? Write(filePath)

    // response itself returns a future, which we flatten
    val iOResultFuture = future.mapTo[Future[IOResult]].flatten
    val responseFuture = iOResultFuture.map(iOResult => {
      iOResult.status match {
        case Success(Done) => FileRecorded(requestId)
        case Failure(_) => FileRecordError("RecordingFileError")
      }
    })

    responseFuture.pipeTo(sender())
  }
}

// Actor for handling async file reading and writing
object FileActor {
  case class Write(filePath: String)
}
class FileActor extends Actor with ActorLogging {
  import FileActor._
  implicit val executionContext: ExecutionContext = context.dispatcher
  // materializer for akka streams API (file IO)
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  override def preStart(): Unit = log.info("File Actor Started")
  override def postStop(): Unit = log.info("File Actor stopped")

  override def receive: Receive = {
    case Write(filePath: String) =>
      val file = Paths.get(filePath)
      val text: Source[String, NotUsed] = Source(1 to 10).map(_ => Random.alphanumeric.take(100 * 1024).mkString) // source of 1kB chunks
      sender() ! text.map(t => ByteString(t)).runWith(FileIO.toPath(file))
  }
}
