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

  final case class RecordData(requestId: Long)
  final case class FileRecorded(requestId: Long)
  final case class FileRecordError(reason: String)

  final case class ReadError(reason: String)
  final case class ReadFileRef(requestId: Long)
  final case class RespondRef(requestId: Long, filePath: Option[String]) // holds path to data file
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
    case ReadFileRef(requestId) =>
      log.info("Actor does not yet have available file")
      sender() ! RespondRef(requestId, None)
    case RecordData(requestId) =>
      log.info("Recording Data")
      handleFileWrite(s"./file-storage/$groupId-$deviceId-$requestId.txt", requestId)
      context.become(awaitUploadFile())
    case _ =>
      log.warning("Actor cannot handle message")
      sender() ! ReadError("UnknownMessage")
  }

  def awaitUploadFile(): Receive = {
    case _ =>
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
