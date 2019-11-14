import microsites._

lazy val fmq = project
  .in(file("."))
  .settings(commonSettings)
  .settings(commandSettings)
  .aggregate(core)

lazy val core = (project in file("core"))
  .settings(commonSettings)
  .settings(commandSettings)
  .settings(
    name                := s"${Settings.name}-core",
    libraryDependencies ++= Dependencies.core
  )

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(MdocPlugin)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .dependsOn(core)
  .settings(
    micrositeName           := "fmq",
    micrositeDescription    := "fMQ - Functional ZeroMQ library",
    micrositeAuthor         := "Maksim Ochenashko",
    micrositeGithubOwner    := "iRevive",
    micrositeGithubRepo     := "fmq",
    micrositeBaseUrl        := "/fmq",
    micrositeFooterText     := None,
    micrositeHighlightTheme := "atom-one-light",
    micrositePalette := Map(
      "brand-primary"   -> "#3e5b95",
      "brand-secondary" -> "#294066",
      "brand-tertiary"  -> "#2d5799",
      "gray-dark"       -> "#49494B",
      "gray"            -> "#7B7B7E",
      "gray-light"      -> "#E5E5E6",
      "gray-lighter"    -> "#F4F3F4",
      "white-color"     -> "#FFFFFF"
    ),
    micrositeCompilingDocsTool := WithMdoc,
    mdocIn                     := (sourceDirectory in Compile).value / "mdoc",
    mdoc / fork                := true,
    micrositePushSiteWith      := GitHub4s,
    micrositeGithubToken       := sys.env.get("GITHUB_TOKEN"),
    micrositeExtraMdFiles := Map(
      file("README.md") -> ExtraMdFileConfig(
        "index.md",
        "home",
        Map("title" -> "Home", "section" -> "home", "position" -> "0")
      ),
      file("LICENSE") -> ExtraMdFileConfig(
        "license.md",
        "page",
        Map("title" -> "license", "section" -> "license", "position" -> "101")
      )
    )
  )

lazy val commonSettings = Seq(
  organization                          := Settings.organization,
  scalaVersion                          := Versions.scala_213,
  crossScalaVersions                    := Seq(scalaVersion.value, Versions.scala_212),
  Test / fork                           := true,
  Test / parallelExecution              := false,
  Compile / compile / wartremoverErrors ++= Warts.allBut(Wart.Any, Wart.Nothing), // false positive
  addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.11.0" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
)

lazy val commandSettings = {
  val ci = Command.command("ci") { state =>
    "clean" ::
      "coverage" ::
      "scalafmtSbtCheck" ::
      "scalafmtCheckAll" ::
      "test" ::
      "coverageReport" ::
      "coverageAggregate" ::
      state
  }

  val testAll = Command.command("testAll") { state =>
    "clean" :: "coverage" :: "test" :: "coverageReport" :: "coverageAggregate" :: state
  }

  commands ++= List(ci, testAll)
}

lazy val noPublishSettings = Seq(
  publish         := (()),
  publishLocal    := (()),
  publishArtifact := false,
  publish / skip  := true
)
