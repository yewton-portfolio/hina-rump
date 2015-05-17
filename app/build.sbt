import Dependencies._

lazy val appEventBus = Common.actorProject("app-event-bus")
  .settings(
    libraryDependencies ++= camelDeps ++ akkaCamelDeps ++ camelJettyDeps ++ camelKafkaDeps)
  .dependsOn(appActor)
  .dependsOn(integrationTest % "it")
  .dependsOn(util, core)

lazy val appPushApi = Common.playProject("app-push-api")
  .dependsOn(appPlay)
  .dependsOn(integrationTest % "it")
  .dependsOn(util, core)

lazy val appPushWorker = Common.actorProject("app-push-worker")
  .dependsOn(appActor)
  .dependsOn(integrationTest % "it")
  .dependsOn(util, core)

lazy val appPlay = Common.libProject("app-play")
  .dependsOn(appCore)
  .dependsOn(integrationTest % "it")
  .dependsOn(util, core)

lazy val appActor = Common.libProject("app-actor")
  .dependsOn(appCore)
  .dependsOn(integrationTest % "it")
  .dependsOn(util, core)

lazy val appCore = Common.libProject("app-core")
  .settings(
  libraryDependencies ++= guiceDeps)
  .dependsOn(integrationTest % "it")
  .dependsOn(util, core)

lazy val domainPush = Common.libProject("domain-push")
  .dependsOn(domainCore)
  .dependsOn(integrationTest % "it")
  .dependsOn(util, core)

lazy val domainCore = Common.libProject("domain-core")
  .dependsOn(integrationTest % "it")
  .dependsOn(util, core)

lazy val infraPush = Common.libProject("infra-push")
  .settings(
  libraryDependencies ++= camelBeanstalkDeps)
  .dependsOn(integrationTest % "it")
  .dependsOn(util, core)

lazy val integrationTest = Common.libProject("integration-test")
  .settings(
  libraryDependencies ++= httpClientDeps ++ databaseDeps)
  .dependsOn(util, core)

lazy val util = Common.libProject("util")

lazy val core = Common.libProject("core")

lazy val root = Common.rootProject
  .aggregate(core, util)
  .aggregate(integrationTest)
  .aggregate(infraPush)
  .aggregate(domainCore, domainPush)
  .aggregate(appCore, appActor, appPlay)
  .aggregate(appPushWorker, appPushApi)
