import sbt._

object Versions {
  val scala_212  = "2.12.10"
  val scala_213  = "2.13.1"
  val catsEffect = "2.0.0"
  val fs2        = "2.2.1"
  val zmq        = "0.5.1"
  val scalatest  = "3.1.0"
}

object Dependencies {

  val fs2 = "co.fs2" %% "fs2-io" % Versions.fs2

  val core: Seq[ModuleID] = Seq(
    "org.typelevel" %% "cats-effect"      % Versions.catsEffect,
    "org.zeromq"    % "jeromq"            % Versions.zmq,
    "org.scalatest" %% "scalatest"        % Versions.scalatest % Test,
    "org.typelevel" %% "cats-effect-laws" % Versions.catsEffect % Test,
    "co.fs2"        %% "fs2-io"           % Versions.fs2 % Test
  )

}
