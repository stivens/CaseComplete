import xerial.sbt.Sonatype.sonatypeCentralHost

sonatypeCredentialHost := sonatypeCentralHost

publishTo := sonatypePublishToBundle.value

organization := "io.github.stivens"
name         := "CaseComplete"
homepage     := Some(url("https://github.com/stivens/CaseComplete"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/stivens/CaseComplete"),
    "scm:git@github.com:stivens/CaseComplete.git"
  )
)
licenses := Seq("MIT" -> url("https://github.com/stivens/CaseComplete/blob/main/LICENSE"))
developers := List(
  Developer(
    id = "stivens",
    name = "Jacek Bizub",
    email = "jacekbizub@gmail.com",
    url = url("https://github.com/stivens")
  )
)

version := "0.1.0"

scalaVersion := "3.3.6"

resolvers += "shibboleth-releases" at "https://build.shibboleth.net/maven/releases"
resolvers += Resolver.sonatypeCentralSnapshots

enablePlugins(ScalafixPlugin, SemanticdbPlugin)

inThisBuild(
  List(
    semanticdbEnabled := true
  )
)

scalacOptions ++= Seq(
  "-Wunused:imports",
  "-feature",
  "-language:implicitConversions",
  "-no-indent",
  "-Xmax-inlines",
  "128",
  "-Xfatal-warnings"
)

libraryDependencies ++= Seq(
  // scalatest
  "org.scalactic" %% "scalactic" % "3.2.19",
  "org.scalatest" %% "scalatest" % "3.2.19" % "test"
)

lazy val root = project
  .in(file("."))
  .settings()
