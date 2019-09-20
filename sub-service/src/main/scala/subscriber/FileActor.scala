package subscriber

import java.nio.file.Paths

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorLogging}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import akka.pattern.pipe

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Random, Success}

// Actor for handling async file reading and writing
object FileActor {
  case class Write(requestId: Long, filePath: String)
  case class FileRecorded(requestId: Long, filePath: String)
  case object FileRecordedFailed
}
class FileActor extends Actor with ActorLogging {
  import FileActor._

  implicit val executionContext: ExecutionContext = context.dispatcher
  // materializer for akka streams API (file IO)
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  override def preStart(): Unit = log.info("File Actor Started")

  override def postStop(): Unit = log.info("File Actor stopped")

  override def receive: Receive = {
    case Write(requestId: Long, filePath: String) =>
      log.info("Writing file for requestId: {} to filePath: {}", requestId, filePath)
      val file = Paths.get(filePath)
      val text: Source[String, NotUsed] = Source(1 to 10).map(_ => Random.alphanumeric.take(100 * 1024).mkString) // source of 1kB chunks
      // Future[IOResult] - map to a Device message which is piped to Device on completion
      text.map(t => ByteString(t)).runWith(FileIO.toPath(file))
        .map(iOResult => {
          iOResult.status match {
            case Success(Done) =>
              Device.RecordFileResponse(requestId, filePath)
            case Failure(_) =>
              Device.RecordFileError("RecordingFileError")
          }
        }).pipeTo(sender())
  }
}
