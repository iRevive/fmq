import sbt._

object Versions {
  val scala_212        = "2.12.10"
  val scala_213        = "2.13.5"
  val catsEffect       = "3.0.0"
  val fs2              = "3.0.0-M9"
  val jeromq           = "0.5.2"
  val scalatest        = "3.2.3"
  val collectionCompat = "2.4.2"
  val betterMonadicFor = "0.3.1"
}

object Dependencies {

  val fs2 = "co.fs2" %% "fs2-io" % Versions.fs2

  def core(scalaVersion: String): Seq[ModuleID] =
    Seq(
      "org.typelevel"          %% "cats-effect"             % Versions.catsEffect,
      "org.zeromq"              % "jeromq"                  % Versions.jeromq,
      "org.scala-lang.modules" %% "scala-collection-compat" % Versions.collectionCompat,
      "org.scala-lang"          % "scala-reflect"           % scalaVersion       % Provided,
      "org.scalatest"          %% "scalatest"               % Versions.scalatest % Test,
      "co.fs2"                 %% "fs2-io"                  % Versions.fs2       % Test,
      compilerPlugin("com.olegpy" %% "better-monadic-for" % Versions.betterMonadicFor)
    )

  val extras: Seq[ModuleID] = Seq(
    "co.fs2"        %% "fs2-io"    % Versions.fs2,
    "org.scalatest" %% "scalatest" % Versions.scalatest % Test
  )

}
