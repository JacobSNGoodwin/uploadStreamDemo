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
      fileActor.tell(Write(54321L, "./file-storage/test-file.txt"), probe.ref)
      probe.expectMsg(Device.RecordFileResponse(54321L, "./file-storage/test-file.txt"))
    }
  }

}
