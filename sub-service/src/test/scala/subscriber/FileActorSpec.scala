package subscriber

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._

class FileActorSpec extends TestKit(ActorSystem("FileActorSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
  with Matchers {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
  import FileActor._
  "A FileActor" should {
    "return a RecordFileResponse when asked for file Write" in {
      val probe = TestProbe()
      val fileActor = system.actorOf(Props[FileActor])

      implicit val timeout: Timeout = Timeout(3.second)
      fileActor.tell(Write(54321L, "./file-storage/test-file.txt", fileActor), probe.ref)
      probe.expectMsg(FileRecorded(54321L, "./file-storage/test-file.txt", fileActor))
    }

    "return a response on file upload" in {
      // more of an integration test
      val probe = TestProbe()
      val fileActor = system.actorOf(Props[FileActor])
      val filePath = "./file-storage/test-file.txt"


      fileActor.tell(Write(54321L, filePath, fileActor), probe.ref)
      probe.expectMsg(FileRecorded(54321L, filePath, fileActor))


      fileActor.tell(FileUpload(12345L, filePath), probe.ref)
      probe.expectMsgType[FileUploadResponse](15.seconds)

    }
  }

}
