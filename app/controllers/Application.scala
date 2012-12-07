package controllers

import play.api._
import libs.iteratee._
import play.api.mvc._
import akka.actor._
import akka.util.duration._
import akka.pattern._
import play.api.libs.concurrent._
import akka.util.{Duration, Timeout}
import java.io._
import akka.dispatch.Await
import actors._

object Application extends Controller {

  val system = {
    val config = Play.current.configuration.underlying
    ActorSystem("pomodoro", config.getConfig("pomodoro").withFallback(config))
  }
  val actorRepository = system.actorOf(Props[ActorRepository])

  implicit val timeout = Timeout(1 second)

  def index = Action {
    Async {
      implicit val timeout = Timeout(10 seconds)
      for {
        globalOrenoPomodoro <- (actorRepository ? GetPomodoro("global")).mapTo[ActorRef].asPromise
        inPomodoroNow <- (globalOrenoPomodoro ? InPomodoroNow).mapTo[Boolean].asPromise
      } yield {
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

  // TODO Make this async not to block current thread
  def actorForUserName(username: String): ActorRef = {
    Await.result(actorRepository ? GetPomodoro(username), 10 second).asInstanceOf[ActorRef]
  }

  def watch(username: String) = WebSocket.using[String] { req =>

  // Start an actor
    val orenoPomodoro = actorForUserName(username)

    val session = models.Session.create

    orenoPomodoro ! Connect(session)

    val outputStream = new PipedOutputStream()
    val inputStream = new PipedInputStream(outputStream)
    val writer = new PrintWriter(outputStream)

    val interval = system.scheduler.schedule(0 seconds, 1 second) {
      implicit val timeout = Timeout(10 seconds)
      (orenoPomodoro ? InPomodoroNow).mapTo[Boolean].asPromise.map { inPomodoroNow =>
        writer.print(
          if (inPomodoroNow)
            "ポモドーロ中"
          else
            "休憩中"
        )
        writer.flush()
      }
    }

    val out: Enumerator[String] = Enumerator.fromStream(inputStream).map(bytes => new String(bytes, "UTF-8"))

    val in = Iteratee.foreach[String](println).mapDone { _ =>
      println("Disconnected")
      orenoPomodoro ! Disconnect(session)
      interval.cancel()
    }

    (in, out)
  }
  
}
