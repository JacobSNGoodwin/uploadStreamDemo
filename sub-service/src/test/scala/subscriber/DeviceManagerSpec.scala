package subscriber

import akka.actor.{ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class DeviceManagerSpec extends TestKit(ActorSystem("DeviceGroupSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
  with Matchers {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A device manager" should {
    "be able to register a device group" in {
      val probe = TestProbe()
      val managerActor = system.actorOf(DeviceManager.props, "deviceManager1")

      managerActor.tell(DeviceManager.RequestTrackDevice("group", "device1"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)
      val deviceActor1 = probe.lastSender

      managerActor.tell(DeviceManager.RequestTrackDevice("group", "device2"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)
      val deviceActor2 = probe.lastSender
      deviceActor1 should !==(deviceActor2)
    }

    "return same actor for same deviceId" in {
      val probe = TestProbe()
      val managerActor = system.actorOf(DeviceManager.props, "deviceManager2")

      managerActor.tell(DeviceManager.RequestTrackDevice("group", "device1"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)
      val deviceActor1 = probe.lastSender

      managerActor.tell(DeviceManager.RequestTrackDevice("group", "device1"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)
      val deviceActor2 = probe.lastSender

      deviceActor1 should ===(deviceActor2)
    }

    "be able to list active groups" in {
      val probe = TestProbe()
      val managerActor = system.actorOf(DeviceManager.props, "deviceManager3")

      managerActor.tell(DeviceManager.RequestTrackDevice("group1", "device1"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)

      managerActor.tell(DeviceManager.RequestTrackDevice("group2", "device1"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)

      managerActor.tell(DeviceManager.RequestGroupList(requestId = "0"), probe.ref)
      probe.expectMsg(DeviceManager.ReplyGroupList(requestId = "0", Set("group1", "group2")))
    }

    "be able to list active groups after one shuts down" in {
      val probe = TestProbe()
      val managerActor = system.actorOf(DeviceManager.props, "deviceManager4")

      managerActor.tell(DeviceManager.RequestTrackDevice("group1", "device1"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)

      managerActor.tell(DeviceManager.RequestTrackDevice("group2", "device1"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)

      managerActor.tell(DeviceManager.RequestGroupList(requestId = "0"), probe.ref)
      probe.expectMsg(DeviceManager.ReplyGroupList(requestId = "0", Set("group1", "group2")))

      managerActor.tell(DeviceManager.RequestGroupRef("1", "group1"), probe.ref)
      val response = probe.expectMsgType[DeviceManager.ReplyGroupRef]

      val toShutDown = response.groupRef

      probe.watch(toShutDown)
      toShutDown ! PoisonPill
      probe.expectTerminated(toShutDown)

      // using awaitAssert to retry because it might take longer for the groupActor
      // to see the Terminated, that order is undefined
      probe.awaitAssert {
        managerActor.tell(DeviceManager.RequestGroupList(requestId = "1"), probe.ref)
        probe.expectMsg(DeviceManager.ReplyGroupList(requestId = "1", Set("group2")))
      }
    }
  }

}
