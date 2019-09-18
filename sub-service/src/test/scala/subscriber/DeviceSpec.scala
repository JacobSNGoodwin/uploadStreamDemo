package subscriber

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._



class DeviceSpec extends TestKit(ActorSystem("DeviceSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
  with Matchers {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A device actor" should {
    "respond with None if no data is available" in {
      val deviceActor = system.actorOf(Device.props("0001", "0001"))

      deviceActor ! Device.ReadFile(1)
      val response = expectMsgType[Device.ReadFileResponse]
      response.requestId should ===(1L)
      response.filePath should ===(None)
    }

    "respond to successful file write" in {
      val deviceActor = system.actorOf(Device.props("0001", "0002"))
      implicit val timeout: Timeout = Timeout(3.second)

      deviceActor ! Device.RecordFile(2L)
      expectMsg(Device.RecordFileResponse(2L))
    }

    "respond with file path if there is a recorded file" in {
      val groupId = "0001"
      val deviceId = "0002"
      val deviceActor = system.actorOf(Device.props(groupId, deviceId))

      implicit val timeout: Timeout = Timeout(3.second)

      deviceActor ! Device.RecordFile(3L)
      deviceActor ! Device.ReadFile(4L)
      val response = expectMsgType[Device.ReadFileResponse]
      response.requestId should ===(4L)
      response.filePath should===(Some(s"./file-storage/$groupId-$deviceId-3.txt"))
    }
  }
}
