import sbt._

object Versions {
  val scala_212  = "2.12.10"
  val scala_213  = "2.13.1"
  val catsEffect = "2.1.3"
  val fs2        = "2.3.0"
  val jeromq     = "0.5.2"
  val scalatest  = "3.1.1"
}

object Dependencies {

  val fs2 = "co.fs2" %% "fs2-io" % Versions.fs2

  val core: Seq[ModuleID] = Seq(
    "org.typelevel" %% "cats-effect" % Versions.catsEffect,
    "org.zeromq"    % "jeromq"       % Versions.jeromq,
    "org.scalatest" %% "scalatest"   % Versions.scalatest % Test,
    "co.fs2"        %% "fs2-io"      % Versions.fs2 % Test
  )

  val extras: Seq[ModuleID] = Seq(
    "co.fs2"        %% "fs2-io"    % Versions.fs2,
    "org.scalatest" %% "scalatest" % Versions.scalatest % Test
  )

}
