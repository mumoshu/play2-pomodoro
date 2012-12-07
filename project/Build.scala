import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "pomodoro"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      "org.scala-stm" %% "scala-stm" % "0.6"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      // Add your own project settings here      
    )

}
