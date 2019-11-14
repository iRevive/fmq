import sbt._

object Settings {
  val organization = "io.github.irevive"
  val name         = "fmq"
}

object Versions {
  val scala_212  = "2.12.10"
  val scala_213  = "2.13.0"
  val catsEffect = "2.0.0"
  val fs2        = "2.1.0"
  val zmq        = "0.5.1"
  val scalatest  = "3.0.8"
}

object Dependencies {

  val core: Seq[ModuleID] = Seq(
    "org.typelevel" %% "cats-effect" % Versions.catsEffect,
    "org.zeromq"    % "jeromq"       % Versions.zmq,
    "co.fs2"        %% "fs2-io"      % Versions.fs2 % Test,
    "org.scalatest" %% "scalatest"   % Versions.scalatest % Test
  )

}
