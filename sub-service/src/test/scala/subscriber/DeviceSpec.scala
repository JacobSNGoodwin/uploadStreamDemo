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

      deviceActor ! Device.ReadFileRef(1)
      val response = expectMsgType[Device.RespondRef]
      response.requestId should ===(1L)
      response.filePath should ===(None)
    }

    "successfully writes a file" in {
      val deviceActor = system.actorOf(Device.props("0001", "0002"))
      implicit val timeout: Timeout = Timeout(3.second)

      deviceActor ! Device.RecordData(9248743L)
      expectMsg(Device.FileRecorded(9248743L))
    }
  }
}
