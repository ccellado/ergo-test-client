ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.1"

val Versions = new {
  val vertxSnapshot    = "4.5.2-20240113.040803-23"
  val ergoCoreSnapshot = "5.0.18-44-cac70444-SNAPSHOT"
}

lazy val root = (project in file("."))
  .settings(
    name := "moonlight.scala.playground",
    resolvers ++= Seq(
      "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
      "Bintray" at "https://jcenter.bintray.com/", // for org.ethereum % leveldbjni-all
      "SonaType" at "https://oss.sonatype.org/content/groups/public",
      //      "SonaType Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots/",
      "Typesafe maven releases" at "https://dl.bintray.com/typesafe/maven-releases/",
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
    ) ++
      Resolver.sonatypeOssRepos("snapshots"),
    libraryDependencies ++= Seq(
      ("org.ergoplatform" %% "ergo-core" % Versions.ergoCoreSnapshot)
        .cross(CrossVersion.for3Use2_13)
        .exclude("org.scodec", "scodec-bits_2.13"),
      "io.vertx" % "vertx-lang-scala_3" % Versions.vertxSnapshot,
      "io.vertx" % "vertx-core"         % Versions.vertxSnapshot,
      "io.vertx" % "vertx-web"          % Versions.vertxSnapshot,
      "io.netty" % "netty-resolver-dns-native-macos" % "4.1.86.Final" % "runtime" classifier "osx-aarch_64",
      "org.scodec" %% "scodec-core" % "2.2.2"
    )
  )
