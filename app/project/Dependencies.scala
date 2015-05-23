import sbt._
import play.core.PlayVersion

object Dependencies {
  val commonDeps: Seq[ModuleID] = Seq(
    "com.github.nscala-time" %% "nscala-time" % "1.0.0",
    "commons-codec" % "commons-codec" % "1.9",
    "org.slf4j" % "slf4j-api" % "1.7.7",
    "ch.qos.logback" % "logback-classic" % "1.1.1",
    "org.specs2" %% "specs2" % "2.3.12" % "it,test",
    "org.mockito" % "mockito-core" % "1.9.5" % "it,test")

  val scalikeJdbcVersion = "2.0.4"
  val scalikeJdbcDeps = Seq(
    "org.scalikejdbc" %% "scalikejdbc" % scalikeJdbcVersion,
    "org.scalikejdbc" %% "scalikejdbc-config" % scalikeJdbcVersion,
    "org.scalikejdbc" %% "scalikejdbc-test" % scalikeJdbcVersion % "it")

  val databaseDeps = Seq(
    "com.jolbox" % "bonecp" % "0.8.0.RELEASE",
    "mysql" % "mysql-connector-java" % "5.1.30",
    "com.h2database" % "h2" % "1.4.177" % "it") ++ scalikeJdbcDeps

  val httpClientDeps = Seq(
    "net.databinder.dispatch" %% "dispatch-core" % "0.11.1",
    "com.typesafe.play" %% "play" % PlayVersion.current % "it",
    "com.typesafe.play" %% "play-test" % PlayVersion.current % "it")

  val playJsonDeps = Seq(
    "com.typesafe.play" %% "play-json" % PlayVersion.current)

  val configDeps = Seq(
    "com.typesafe" % "config" % "1.2.1")

  val redisDeps = Seq(
    "net.debasishg" %% "redisclient" % "2.13")

  val akkaVersion = "2.3.4"
  val akkaDeps = Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "it")

  val cliDeps = Seq(
    "commons-cli" % "commons-cli" % "1.2")

  val playDeps = Seq(
    "com.typesafe.play" %% "play" % PlayVersion.current,
    "com.typesafe.play" %% "play-test" % PlayVersion.current)

  val jacksonDeps = Seq(
    "com.fasterxml.jackson.core" % "jackson-databind" % "2.5.3")

  val camelVersion = "2.15.1"
  val camelDeps = Seq(
    "org.apache.camel" % "camel-core" % camelVersion,
    "org.apache.camel" % "camel-scala" % camelVersion,
    "org.apache.camel" % "camel-guice" % camelVersion,
    "org.apache.camel" % "camel-test" % camelVersion % "test")

  val camelJettyDeps = Seq(
    "org.apache.camel" % "camel-jetty" % camelVersion)

  val camelNettyHttpDeps = Seq(
    "org.apache.camel" % "camel-netty4-http" % camelVersion,
    "org.apache.camel" % "camel-jackson" % camelVersion) ++ jacksonDeps

  val camelBeanstalkDeps = Seq(
    "org.apache.camel" % "camel-beanstalk" % camelVersion)

  val akkaCamelDeps = Seq(
    "com.typesafe.akka" %% "akka-camel" % akkaVersion)

  val guiceDeps = Seq(
    "net.codingwell" %% "scala-guice" % "4.0.0-beta5")

  val kafkaDeps = Seq(
    "org.apache.kafka" % "kafka-clients" % "0.8.2.1")
}
