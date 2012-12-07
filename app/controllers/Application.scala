package controllers

import play.api._
import play.api.mvc._
import akka.actor._
import akka.util.duration._
import akka.pattern._
import play.api.libs.concurrent._
import akka.util.Timeout

case object InPomodoroNow

class OrenoPomodoro extends Actor {

  val pomodoroDuration = 25 seconds
  val breakDuration = 5 seconds

  var inPomodoroNow = false

  def startPomodoro() {
    Logger.info("Starting a pomodoro")
    inPomodoroNow = true
    context.system.scheduler.scheduleOnce(pomodoroDuration) {
      startBreak()
    }
  }

  def startBreak() {
    Logger.info("Starting a break")
    inPomodoroNow = false
    context.system.scheduler.scheduleOnce(breakDuration) {
      startPomodoro()
    }
  }

  override def preStart() {
    super.preStart()
    startPomodoro()
  }

  def receive: Receive = {
    case InPomodoroNow =>
      sender ! inPomodoroNow
  }
}

object Application extends Controller {

  val system = ActorSystem("pomodoro")
  val orenoPomodoro = system.actorOf(Props[OrenoPomodoro])
  
  def index = Action {
    Async {
      implicit val timeout = Timeout(10 seconds)
      (orenoPomodoro ? InPomodoroNow).mapTo[Boolean].asPromise.map { inPomodoroNow =>
        Ok(
          if (inPomodoroNow)
            "ポモドーロ中"
          else
            "休憩中"
        )
      }
    }
  }
  
}