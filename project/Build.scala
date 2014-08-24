import sbt._
import Keys._

import android.Keys._

object General {
  val settings = Defaults.defaultSettings ++ Seq (
    name := "TryIdris",
    version := "0.1",
    scalaVersion := "2.10.4",
    resolvers             ++= Seq(
      "sonatype-s" at "http://oss.sonatype.org/content/repositories/snapshots",
      "fedorapeople" at "https://codeblock.fedorapeople.org/maven"
    ),
    libraryDependencies   ++= Seq(
      "org.scalaz" %% "scalaz-core" % "7.1.0",
      //"org.scalaz.stream" %% "scalaz-stream" % "0.2-SNAPSHOT",
      "io.argonaut" %% "argonaut" % "6.0.4",
      "me.elrod" %% "tryidris" % "0.1-SNAPSHOT"
    ),
    scalacOptions         := Seq(
      "-encoding", "utf8",
      "-target:jvm-1.6",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-optimise",
      "-Ywarn-value-discard"
    ),
    javacOptions          ++= Seq(
      "-encoding", "utf8",
      "-source", "1.6",
      "-target", "1.6"
    )
  )

  val proguardSettings = Seq (
    useProguard in Android := true,
    proguardOptions in Android += "-keep class scala.Function1",
    proguardOptions in Android += "-keep class scala.PartialFunction",
    proguardOptions in Android += "-keep class scala.util.parsing.combinator.Parsers",
    proguardOptions in Android += "-dontwarn javax.swing.SwingWorker",
    proguardOptions in Android += "-dontwarn javax.swing.SwingUtilities",

    proguardCache in Android += ProguardCache("scalaz") % "org.scalaz",
    proguardCache in Android += ProguardCache("argonaut") % "io.argonaut"
  )

  lazy val fullAndroidSettings =
    General.settings ++
    android.Plugin.androidBuild ++
    proguardSettings
}

object AndroidBuild extends Build {
  lazy val main = Project (
    "TryIdris",
    file("."),
    settings = General.fullAndroidSettings ++ Seq(
      platformTarget in Android := "android-15"
    )
  )

  lazy val tests = Project (
    "tests",
    file("tests"),
    settings = General.settings
  ) dependsOn main
}
