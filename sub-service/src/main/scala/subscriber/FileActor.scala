package subscriber

import java.nio.file.Paths

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.scaladsl.model.ContentTypes
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import akka.pattern.pipe
import akka.stream.alpakka.googlecloud.storage.{GCStorageAttributes, GCStorageExt, GCStorageSettings, StorageObject}
import akka.stream.alpakka.googlecloud.storage.scaladsl.GCStorage
import com.typesafe.config._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success}

// Actor for handling async file reading and writing
object FileActor {
  case class Write(requestId: Long, filePath: String, originalRequester: ActorRef)
  case class FileRecorded(requestId: Long, filePath: String, originalRequester: ActorRef)
  case class FileRecordedFailed(originalRequester: ActorRef)

  case class FileUpload(requestId: Long, filePath: String)
  case class FileUploadResponse(requestId: Long, storageId: String)
  case object FileUploadError
}
class FileActor extends Actor with ActorLogging {
  import FileActor._
  val conf: Config = ConfigFactory.load()
  val bucketName = conf.getString("gcConfig.bucket-name")
  val ChunkSize = 256 * 1024

  // deal with nasty newline issue when loading environment variables
  val gcKey = conf.getString("gcConfig.key").replace("\\n", "\n")
  val newPrivateKeySettings: GCStorageSettings = GCStorageExt(context.system).settings.withPrivateKey(gcKey)


  implicit val executionContext: ExecutionContext = context.dispatcher
  // materializer for akka streams API (file IO)
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  override def preStart(): Unit = log.info("File Actor Started")

  override def postStop(): Unit = log.info("File Actor stopped")

  override def receive: Receive = {
    case Write(requestId: Long, filePath: String, originalRequester: ActorRef) =>
      log.info("Writing file for requestId: {} to filePath: {}", requestId, filePath)
      val file = Paths.get(filePath)
      val text: Source[String, NotUsed] = Source(1 to 100).map(_ => Random.alphanumeric.take(10 * 1024).mkString) // source of 1kB chunks
      // Future[IOResult] - map to a Device message which is piped to Device on completion
      text.map(t => ByteString(t)).runWith(FileIO.toPath(file))
        .map(iOResult => {
          iOResult.status match {
            case Success(Done) =>
              FileRecorded(requestId, filePath, originalRequester)
            case Failure(_) =>
              FileRecordedFailed(originalRequester)
          }
        }).pipeTo(sender())
    case FileUpload(requestId, filePath) =>
      log.info("Attempting file upload to bucket '{}' for the following file path: '{}'", bucketName, filePath)
      val file = Paths.get(filePath)
      val byteStringSource = FileIO.fromPath(file, ChunkSize)
      val sink =
        GCStorage
          .resumableUpload(bucketName, file.getFileName.toString, ContentTypes.`text/plain(UTF-8)`, ChunkSize)
          .withAttributes(GCStorageAttributes.settings(newPrivateKeySettings))
      val result: Future[StorageObject] = byteStringSource.runWith(sink)
      result.map(storageObject => {
        FileUploadResponse(requestId, storageObject.id)
      }).pipeTo(sender())
  }
}
