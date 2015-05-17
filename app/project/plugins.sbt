logLevel := Level.Warn

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

// The Typesafe repository
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.8")

addSbtPlugin("com.scalapenos" % "sbt-prompt" % "0.2.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.8.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")
