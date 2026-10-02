val latestRelease = "1.0.1"

inThisBuild(
  List(
    organization := "io.github.stivens",
    version      := "1.0.1",
    scalaVersion := "3.3.8",
    homepage     := Some(url("https://github.com/stivens/CaseComplete")),
    scmInfo      := Some(
      ScmInfo(
        url("https://github.com/stivens/CaseComplete"),
        "scm:git@github.com:stivens/CaseComplete.git"
      )
    ),
    licenses   := Seq("MIT" -> url("https://github.com/stivens/CaseComplete/blob/main/LICENSE")),
    developers := List(
      Developer(
        id = "stivens",
        name = "Jacek Bizub",
        email = "jacekbizub@gmail.com",
        url = url("https://github.com/stivens")
      )
    ),
    versionScheme     := Some("early-semver"),
    semanticdbEnabled := true
  )
)

lazy val casecomplete = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("."))
  .settings(
    name      := "CaseComplete",
    publishTo := localStaging.value,
    scalacOptions ++= Seq(
      "-Wunused:imports",
      "-feature",
      "-language:implicitConversions",
      "-no-indent",
      "-Xmax-inlines",
      "128",
      "-Xfatal-warnings"
    ),
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % Test,
    mimaPreviousArtifacts := Set((organization.value % moduleName.value % latestRelease).cross(crossVersion.value))
  )
  .nativeSettings(
    // test-interface declares a strict scheme, but Scala Native keeps 0.5.x binary compatible;
    // without this, scalatest's older 0.5.x pin is a fatal eviction under sbt 2.
    libraryDependencySchemes += "org.scala-native" % s"test-interface_${ScalaNativePlatform}_${scalaBinaryVersion.value}" % VersionScheme.Always
  )

// The CrossType.Pure platform projects live in ./.jvm, ./.js and ./.native and share ./src;
// this root exists only to aggregate them, so it must not compile ./src itself.
lazy val root = project
  .in(file("."))
  .aggregate(casecomplete.jvm, casecomplete.js, casecomplete.native)
  .settings(
    publish / skip := true,
    // Not redundant: MiMa treats an unset mimaPreviousArtifacts as an error (mimaFailOnNoPrevious),
    // but an explicitly empty one as "nothing to check".
    mimaPreviousArtifacts                := Set.empty,
    Compile / unmanagedSourceDirectories := Nil,
    Test / unmanagedSourceDirectories    := Nil
  )

// Not aggregated by root: building the site is the docs workflow's job, not `sbt test`'s.
lazy val docs = project
  .in(file("website"))
  .enablePlugins(MdocPlugin)
  .dependsOn(casecomplete.jvm)
  .settings(
    // mkdocs.yml's docs_dir points here, so it can't follow sbt 2's target/out/... layout.
    mdocOut        := baseDirectory.value / "target" / "mdoc",
    mdocIn         := baseDirectory.value / "docs",
    mdocVariables  := Map("VERSION" -> latestRelease),
    libraryDependencies += "org.tpolecat" %% "doobie-core" % "1.0.0-RC12"
  )
