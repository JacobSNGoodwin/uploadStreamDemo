package subscriber

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class DeviceGroupSpec extends TestKit(ActorSystem("DeviceGroupSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
  with Matchers {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A device group" should {
    "be able to register a device actor" in {
      val probe = TestProbe()
      val groupActor = system.actorOf(DeviceGroup.props("group"))

      groupActor.tell(DeviceManager.RequestTrackDevice("group", "device1"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)
    }
  }

}
