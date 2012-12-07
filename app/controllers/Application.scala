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
import java.nio.charset.Charset

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

  /**
   *
   * @param username the name of this pomodoro's owner
   * @param pomodoro the duration of each pomodoro in text e.g. "25 minutes", "1500 seconds"
   * @param break the duration of of each break in text e.g. "5 minutes", "300 seconds"
   * @return
   */
  def status(username: String, pomodoro: String, break: String) = Action { implicit request =>
    Ok(views.html.status(username, pomodoro, break))
  }

  // TODO Make this async not to block current thread
  def getOrCreateActor(msg: GetPomodoro): ActorRef = {
    Await.result(actorRepository ? msg, 10 second).asInstanceOf[ActorRef]
  }

  def watch(username: String, pomodoro_duration: String, break_duration: String) = WebSocket.using[String] { req =>

  // Start an actor
    val orenoPomodoro = getOrCreateActor(
      GetPomodoro(username, Duration.parse(pomodoro_duration), Duration.parse(break_duration))
    )

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
            "ポモドーロ中".getBytes(Charset.forName("UTF-8"))
          else
            "休憩中".getBytes(Charset.forName("UTF-8"))
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
