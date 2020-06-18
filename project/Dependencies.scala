import sbt._

object Versions {
  val scala_212  = "2.12.10"
  val scala_213  = "2.13.1"
  val catsEffect = "2.2.0"
  val fs2        = "2.4.4"
  val jeromq     = "0.5.2"
  val scalatest  = "3.2.2"
}

object Dependencies {

  val fs2                      = "co.fs2"        %% "fs2-io"        % Versions.fs2
  def scalaReflect(sv: String) = "org.scala-lang" % "scala-reflect" % sv

  def core(scalaVersion: String): Seq[ModuleID] =
    Seq(
      "org.typelevel" %% "cats-effect"   % Versions.catsEffect,
      "org.zeromq"     % "jeromq"        % Versions.jeromq,
      "org.scala-lang" % "scala-reflect" % scalaVersion       % Provided,
      "co.fs2"        %% "fs2-io"        % Versions.fs2       % Test,
      "com.disneystreaming" %% "weaver-framework" % "0.4.0" % Test
    )

  val extras: Seq[ModuleID] = Seq(
    "co.fs2"        %% "fs2-io"    % Versions.fs2,
    "com.disneystreaming" %% "weaver-framework" % "0.4.0" % Test
  )

}
