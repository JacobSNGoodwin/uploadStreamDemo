package subscriber

import akka.actor.{Actor, ActorLogging, Props}

object SupervisingActor {
  def props(): Props = Props(new SupervisingActor())
}
class SupervisingActor extends Actor with ActorLogging {
  override def preStart(): Unit = log.info("Supervising Actor started")
  override def postStop(): Unit = log.info("Supervising Actor stopped")

  override def receive: Receive = {
    case message => log.info(s"Supervising actor has received the following message: ${message}")
  }
}
