import sbt._

object Versions {
  val scala_212  = "2.12.10"
  val scala_213  = "2.13.1"
  val catsEffect = "2.3.1"
  val fs2        = "2.5.1"
  val jeromq     = "0.5.2"
  val scalatest  = "3.2.3"
}

object Dependencies {

  val fs2                      = "co.fs2"        %% "fs2-io"        % Versions.fs2
  def scalaReflect(sv: String) = "org.scala-lang" % "scala-reflect" % sv

  def core(scalaVersion: String): Seq[ModuleID] =
    Seq(
      "org.typelevel" %% "cats-effect"   % Versions.catsEffect,
      "org.zeromq"     % "jeromq"        % Versions.jeromq,
      "org.scala-lang" % "scala-reflect" % scalaVersion       % Provided,
      "org.scalatest" %% "scalatest"     % Versions.scalatest % Test,
      "co.fs2"        %% "fs2-io"        % Versions.fs2       % Test
    )

  val extras: Seq[ModuleID] = Seq(
    "co.fs2"        %% "fs2-io"    % Versions.fs2,
    "org.scalatest" %% "scalatest" % Versions.scalatest % Test
  )

}
