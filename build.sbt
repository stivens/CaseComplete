organization := "io.github.stivens"
name         := "CaseComplete"
homepage     := Some(url("https://github.com/stivens/CaseComplete"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/stivens/CaseComplete"),
    "scm:git@github.com:stivens/CaseComplete.git"
  )
)
developers := List(
  Developer(
    id = "stivens",
    name = "Jacek Bizub",
    email = "jacekbizub@gmail.com",
    url = url("https://github.com/stivens")
  )
)

version := "0.1.0-SNAPSHOT"

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
