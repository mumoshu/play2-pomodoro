package actors

import akka.util.{Timeout, Duration}
import akka.actor.{ActorLogging, Actor, ActorRef}
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.duration._

case class OK(reply: Any)

case class Retry(retries: Int, interval: Duration, destination: ActorRef, message: AnyRef)

class RetryingActor extends Actor with ActorLogging {
  implicit val timeout = Timeout(1 second)
  protected def receive: Receive = {
    LoggingReceive {
      case retryMessage @ Retry(retries, interval, destination, message) if retries >= 0 =>
        val s = sender
        val preservedSelf = self
        try {
          (destination ? message).map { reply =>
            s ! OK(reply)
          }
        } catch {
          case e: AskTimeoutException =>
            preservedSelf ! retryMessage.copy(retries = retryMessage.retries - 1)
        }
    }
  }
}
