import xerial.sbt.Sonatype.sonatypeCentralHost

inThisBuild(
  List(
    organization := "io.github.stivens",
    version      := "0.3.0",
    scalaVersion := "3.3.8",
    homepage     := Some(url("https://github.com/stivens/CaseComplete")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/stivens/CaseComplete"),
        "scm:git@github.com:stivens/CaseComplete.git"
      )
    ),
    licenses := Seq("MIT" -> url("https://github.com/stivens/CaseComplete/blob/main/LICENSE")),
    developers := List(
      Developer(
        id = "stivens",
        name = "Jacek Bizub",
        email = "jacekbizub@gmail.com",
        url = url("https://github.com/stivens")
      )
    ),
    versionScheme          := Some("early-semver"),
    semanticdbEnabled      := true,
    sonatypeCredentialHost := sonatypeCentralHost
  )
)

lazy val casecomplete = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("."))
  .settings(
    name := "CaseComplete",
    // sonatypePublishToBundle is defined per project by sbt-sonatype, so this cannot join
    // sonatypeCredentialHost in inThisBuild.
    publishTo := sonatypePublishToBundle.value,
    scalacOptions ++= Seq(
      "-Wunused:imports",
      "-feature",
      "-language:implicitConversions",
      "-no-indent",
      "-Xmax-inlines",
      "128",
      "-Xfatal-warnings"
    ),
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.2.19" % Test,
    // Empty only while the next release is an intentional new binary-compatibility baseline.
    // Once 1.0.0 ships, set: Set(organization.value %%% moduleName.value % "1.0.0")
    mimaPreviousArtifacts := Set.empty
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
