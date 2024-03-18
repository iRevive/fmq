import sbt._

object Versions {
  val scala_213  = "2.13.8"
  val catsEffect = "3.4.4"
  val fs2        = "3.10.0"
  val jeromq     = "0.5.3"
  val weaver     = "0.8.1"
  val bm4        = "0.3.1"
}

object Dependencies {

  val catsEffect = "org.typelevel"       %% "cats-effect-std"    % Versions.catsEffect
  val fs2        = "co.fs2"              %% "fs2-io"             % Versions.fs2
  val jeromq     = "org.zeromq"           % "jeromq"             % Versions.jeromq
  val weaver     = "com.disneystreaming" %% "weaver-cats"        % Versions.weaver
  val bm4        = "com.olegpy"          %% "better-monadic-for" % Versions.bm4

  def scalaReflect(version: String): ModuleID = "org.scala-lang" % "scala-reflect" % version

  def core(scalaVersion: String): Seq[ModuleID] =
    Seq(
      catsEffect,
      jeromq,
      scalaReflect(scalaVersion) % Provided,
      fs2                        % Test,
      weaver                     % Test,
      compilerPlugin(bm4)
    )

  val extras: Seq[ModuleID] = Seq(fs2, weaver % Test)

}
