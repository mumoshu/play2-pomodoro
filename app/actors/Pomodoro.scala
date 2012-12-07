package actors

import akka.util.duration._
import akka.actor.{Cancellable, ActorRef, ActorLogging, Actor}
import akka.event.{BusLogging, LoggingReceive}
import akka.util.Duration

case object InPomodoroNow
case class Connect(session: models.Session)
case class Disconnect(session: models.Session)
case class EveryoneDisconnected(pomodoro: ActorRef)
case object GiveMeKey
case class SetKey(key: String)

/**
 * Stateful actors for Pomodoro timers
 */
case class Pomodoro(repository: ActorRef, pomodoroDuration: Duration = 25 minutes, breakDuration: Duration = 5 minutes) extends Actor with ActorLogging {

  var key: Option[String] = None
  var inPomodoroNow = false
  var sessions = Map.empty[String, models.Session]
  var pomodoroTimer: Option[Cancellable] = None
  var breakTimer: Option[Cancellable] = None

  def startPomodoro() {
    log.debug("Starting a pomodoro: " + this.toString)
    inPomodoroNow = true
    breakTimer = None
    pomodoroTimer = Some(
      context.system.scheduler.scheduleOnce(pomodoroDuration) {
        startBreak()
      }
    )
  }

  def startBreak() {
    log.info("Starting a break")
    inPomodoroNow = false
    pomodoroTimer = None
    breakTimer = Some(
      context.system.scheduler.scheduleOnce(breakDuration) {
        startPomodoro()
      }
    )
  }

  override def preStart() {
    super.preStart()
    startPomodoro()
  }

  def receive: Receive = {
    LoggingReceive {
      case InPomodoroNow =>
        sender ! inPomodoroNow
      case Connect(session) =>
        sessions += session.uuid -> session
      case Disconnect(session) =>
        sessions -= session.uuid
        if (sessions.isEmpty)
          repository ! EveryoneDisconnected(self)
      case SetKey(newKey) =>
        key = Some(newKey)
        sender ! newKey
      case GiveMeKey =>
        sender ! key
    }
  }

  override def postStop() {
    super.postStop()

    pomodoroTimer.foreach { t => t.cancel() }
    breakTimer.foreach { t => t.cancel() }
  }
}
