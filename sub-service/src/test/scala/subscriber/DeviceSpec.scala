package subscriber

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}


class DeviceSpec extends TestKit(ActorSystem("DeviceSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
  with Matchers {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A device actor" should {
    "should response with None if no data is available" in {
      val deviceActor = system.actorOf(Device.props("0001", "0002"))

      deviceActor ! Device.ReadRef(1)
      val response = expectMsgType[Device.RespondRef]
      response.requestId should ===(1L)
      response.value should ===(None)

    }
  }
}
