package subscriber

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import subscriber.Device._
import subscriber.FileActor._

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
    "reply to registration requests" in {
      val probe = TestProbe()
      val deviceActor = system.actorOf(Device.props("group", "device"))

      deviceActor.tell(DeviceManager.RequestTrackDevice("group", "device"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)
      probe.lastSender should ===(deviceActor)
    }

    "ignore wrong registration requests" in {
      val probe = TestProbe()
      val deviceActor = system.actorOf(Device.props("group", "device"))

      deviceActor.tell(DeviceManager.RequestTrackDevice("wrongGroup", "device"), probe.ref)
      probe.expectNoMessage(500.milliseconds)

      deviceActor.tell(DeviceManager.RequestTrackDevice("group", "Wrongdevice"), probe.ref)
      probe.expectNoMessage(500.milliseconds)
    }

    "respond with None if no data is available" in {
      val probe = TestProbe()
      val deviceActor = system.actorOf(Device.props("0001", "0001"))

      deviceActor.tell(Device.ReadFiles(1), probe.ref)
      val response = probe.expectMsgType[Device.ReadFilesResponse]
      response.requestId should ===(1L)
      response.filePaths should ===(None)
    }

    "respond to successful file write" in {
      val groupId = "0001"
      val deviceId = "0002"
      val probe = TestProbe()
      val deviceActor = system.actorOf(Device.props(groupId, deviceId))
      implicit val timeout: Timeout = Timeout(3.second)

      deviceActor.tell(DeviceManager.RequestDeviceRecord(2L, groupId, deviceId), probe.ref)
      val response = probe.expectMsgType[RecordFileResponse]
      response.requestId should===(2L)
      response.filePath should===(s"./file-storage/$groupId-$deviceId-2.txt")
    }

    "respond with set of file paths if there is at least one recorded file" in {
      val groupId = "0002"
      val deviceId = "0003"
      val probe = TestProbe()

      val deviceActor = system.actorOf(Device.props(groupId, deviceId))

      implicit val timeout: Timeout = Timeout(3.second)

      deviceActor.tell(DeviceManager.RequestDeviceRecord(1L, groupId, deviceId), probe.ref)
      probe.expectMsgType[Device.RecordFileResponse]
      deviceActor.tell(DeviceManager.RequestDeviceRecord(2L, groupId, deviceId), probe.ref)
      probe.expectMsgType[Device.RecordFileResponse] // to make sure we have responses before reading files

      deviceActor.tell(Device.ReadFiles(1L), probe.ref)
      val response = probe.expectMsgType[Device.ReadFilesResponse]
      response.requestId should ===(1L)
      response.filePaths should ===(Some(Set(
        s"./file-storage/$groupId-$deviceId-1.txt",
        s"./file-storage/$groupId-$deviceId-2.txt",
      )))
    }

    "respond to successful file upload" in {
      val groupId = "0002"
      val deviceId = "0003"
      val probe = TestProbe()

      val deviceActor = system.actorOf(Device.props(groupId, deviceId))

      implicit val timeout: Timeout = Timeout(3.second)

      deviceActor.tell(DeviceManager.RequestDeviceRecord(1L, groupId, deviceId), probe.ref)
      probe.expectMsgType[Device.RecordFileResponse]
      deviceActor.tell(DeviceManager.RequestDeviceRecord(2L, groupId, deviceId), probe.ref)
      probe.expectMsgType[Device.RecordFileResponse] // to make sure we have responses before reading files

      deviceActor.tell(DeviceManager.RequestDeviceUpload(1L, groupId, deviceId), probe.ref)

      val responseMessages = probe.receiveN(2, 15.seconds): Seq[AnyRef]
      responseMessages should ===(Seq(Device.UploadFilesResponse(1L), Device.UploadFilesResponse(1L)))
    }

  }
}
