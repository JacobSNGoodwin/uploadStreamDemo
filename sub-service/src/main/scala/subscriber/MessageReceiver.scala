package subscriber

import akka.actor.{Actor, ActorLogging}

object MessageReceiver {
  case object Ack
  case object StreamInitialized
  case object StreamCompleted
  final case class StreamFailure(ex: Throwable)
}
class MessageReceiver extends Actor with ActorLogging {
  import MessageReceiver._
  override def receive: Receive = {
    case StreamInitialized =>
      log.info("Stream initialized!")
      sender() ! Ack // ack to allow the stream to proceed sending more elements
    case el: String =>
      log.info("Received element: {}", el)
      sender() ! Ack // ack to allow the stream to proceed sending more elements
    case StreamCompleted =>
      log.info("Stream completed!")
    case StreamFailure(ex) =>
      log.error(ex, "Stream failed!")

  }
}
