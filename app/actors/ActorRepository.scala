package actors

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.duration._
import akka.util.Timeout

case class GetPomodoro(key: String)

class ActorRepository extends Actor with ActorLogging {

  // TODO Use STM
  var actors = Map.empty[String, ActorRef]

  implicit val timeout = Timeout(1 second)

  def receive: Receive = {
    LoggingReceive {
      case EveryoneDisconnected(pomodoro: ActorRef) =>
        (pomodoro ? GiveMeKey).mapTo[Option[String]].map {
          case Some(key) =>
            context.stop(pomodoro)
            // TODO Make this atomic
            actors -= key
            log.debug("actors: " + actors)
          case _ =>
            throw new RuntimeException("Key is not set for the pomodoro actor: " + pomodoro)
        }
      case m @ GetPomodoro(username: String) =>
        log.info(m.toString)
        val actor = actors.get(username).getOrElse(
          context.actorOf(Props[Pomodoro], "pomodoro-" + username)
        )
        // TODO Make this atomic
        actors += username -> actor
        val preservedSender = sender
        (actor ? SetKey(username)).mapTo[String].map { _ =>
          preservedSender ! actor
        }
    }
  }

  override def postStop() {
    super.postStop()

    actors.foreach { case (key, actor) =>
      context.stop(actor)
    }
  }
}
