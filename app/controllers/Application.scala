package controllers

import play.api._
import libs.iteratee._
import play.api.mvc._
import akka.actor._
import akka.util.duration._
import akka.pattern._
import play.api.libs.concurrent._
import akka.util.Timeout
import java.io._

case object InPomodoroNow

class OrenoPomodoro extends Actor {

  val pomodoroDuration = 25 seconds
  val breakDuration = 5 seconds

  var inPomodoroNow = false

  def startPomodoro() {
    Logger.info("Starting a pomodoro: " + this.toString)
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
  val globalOrenoPomodoro = system.actorOf(Props[OrenoPomodoro])

  def index = Action {
    Async {
      implicit val timeout = Timeout(10 seconds)
      (globalOrenoPomodoro ? InPomodoroNow).mapTo[Boolean].asPromise.map { inPomodoroNow =>
        Ok(
          if (inPomodoroNow)
            "ポモドーロ中"
          else
            "休憩中"
        )
      }
    }
  }

  def status(username: String) = Action { implicit request =>
    Ok(views.html.status(username))
  }

  var actors = Map.empty[String, ActorRef]

  def actorForUserName(username: String) = {
    actors = actors.updated(username, actors.get(username).getOrElse {
      system.actorOf(Props[OrenoPomodoro])
    })
    actors.get(username).get
  }

  def watch(username: String) = WebSocket.using[String] { req =>

    val outputStream = new PipedOutputStream()
    val inputStream = new PipedInputStream(outputStream)
    val writer = new PrintWriter(outputStream)

    // Start an actor
    val orenoPomodoro = actorForUserName(username)

    val out: Enumerator[String] = Enumerator.fromStream(inputStream).map(bytes => new String(bytes, "UTF-8"))

    val interval = system.scheduler.schedule(0 seconds, 1 second) {
      implicit val timeout = Timeout(10 seconds)
      (orenoPomodoro ? InPomodoroNow).mapTo[Boolean].asPromise.map { inPomodoroNow =>
        writer.println(
          if (inPomodoroNow)
            "ポモドーロ中"
          else
            "休憩中"
        )
        writer.flush()
      }
    }

    val in = Iteratee.foreach[String](println).mapDone { _ =>
      println("Disconnected")
      interval.cancel()
    }

    (in, out)
  }
  
}
