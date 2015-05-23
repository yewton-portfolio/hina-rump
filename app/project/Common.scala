import com.scalapenos.sbt.prompt.SbtPrompt.autoImport._
import com.typesafe.sbt.SbtScalariform.{ScalariformKeys, scalariformSettingsWithIt}
import sbt.Keys._
import sbt._

import scalariform.formatter.preferences._

object Common {

  /**
   * sbt run した時に読み込まれる設定ファイル
   */
  val runConfigFile = file("conf/sbt-run.conf")

  /**
   * sbt run した時に読み込まれるログ設定ファイル
   */
  val runLogbackConfigFile = file("conf/logback-development.xml")

  /**
   * テスト用の何もしないロガー設定ファイル
   */
  val testLogbackConfigFile = file("conf/test-logback.xml")

  /**
   * 単体テスト用の設定ファイル
   */
  val utConfigFile = file("conf/unit-test.conf")

  /**
   * 結合テスト用の設定ファイル
   */
  val itConfigFile = file("conf/integration-test.conf")

  /**
   * Jenkins用の設定ファイル
   */
  val jenkinsConfigFile = file("conf/jenkins.conf")

  /**
   * コンパイル共通の設定
   */
  val compileSettings: Seq[Setting[_]] = Seq(
    organization := "net.yewton",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.11.6",
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-language:_"),
    fork := true, // javaOptionsはfork時にのみ設定したoptionが反映される
    javacOptions ++= Seq("-encoding", "UTF-8"),
    // http://d.hatena.ne.jp/xuwei/20130207/1360203782
    javaOptions ++= sys.process.javaVmArguments.filter(
      a => Seq("-Xmx", "-Xms", "-XX").exists(a.startsWith)),
    javaOptions in run ++= Seq(
      "-Dconfig.file=" + runConfigFile.getAbsolutePath,
      "-Dlogback.configurationFile=" + runLogbackConfigFile.getAbsolutePath))

  /**
   * テスト共通設定
   */
  val testSettings = Seq(
    baseDirectory := file("./"),
    javaOptions ++= Seq(
      "-Dlogger.file=" + testLogbackConfigFile.getAbsolutePath,
      "-Dlogback.configurationFile=" + testLogbackConfigFile.getAbsolutePath))

  /**
   * 単体テスト用の設定
   */
  val utSettings = Seq(
    javaOptions in test += "-Dconfig.file=" + utConfigFile.getAbsolutePath)

  /**
   * 結合テスト用の設定
   */
  val itSettings = Defaults.itSettings ++ Seq(
    javaOptions += "-Dconfig.file=" + itConfigFile.getAbsolutePath,
    parallelExecution in ThisBuild := false,
    // コレがないと FakeApplication が同時に立ち上がって死ぬ
    concurrentRestrictions in ThisBuild := Seq(Tags.limit(Tags.All, 1)))

  val specs2Resolvers = Seq(
    "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
  )

  val typesafeResolvers = Seq(
    "typesafe" at "http://repo.typesafe.com/typesafe/releases/"
  )

  val commonResolvers = specs2Resolvers ++ typesafeResolvers

  /**
   * アプリケーション配布用の設定
   * @see https://github.com/playframework/playframework/blob/2.3.x/framework/src/sbt-plugin/src/main/scala/PlaySettings.scala#L276
   */
  val distSettings: Seq[Def.Setting[_]] = {
    import com.typesafe.sbt.SbtNativePackager.{Universal, packageArchetype}
    packageArchetype.java_application ++ Seq(
      mappings in Universal ++= {
        val confDir = "conf"
        val confDirectory = (baseDirectory in LocalRootProject).value / confDir
        val confDirectoryLen = confDirectory.getCanonicalPath.length
        val excludeFiles = Seq(
          utConfigFile,
          itConfigFile,
          jenkinsConfigFile,
          testLogbackConfigFile).map(_.getCanonicalFile) ++ Seq("routes").map(confDirectory / _)
        val pathFinder = confDirectory.***.---(PathFinder(excludeFiles))
        pathFinder.get map {
          confFile: File =>
            confFile -> (confDir + "/" + confFile.getCanonicalPath.substring(confDirectoryLen))
        }
      },
      mappings in Universal ++= {
        val dirName = "scripts"
        val scriptDirectory = (baseDirectory in LocalRootProject).value / dirName
        scriptDirectory.listFiles.toSeq.map { file: File =>
          file -> s"$dirName/${file.getName}"
        }
      },
      sources in(Compile, doc) := Seq.empty,
      publishArtifact in(Compile, packageDoc) := false,
      name in Universal <<= normalizedName
    )
  }

  /**
   * 全プロジェクトの共通設定
   */
  val commonSettings = compileSettings ++
    inConfig(Test)(testSettings ++ utSettings) ++
    inConfig(IntegrationTest)(testSettings ++ itSettings) ++
    scalariformSettingsWithIt ++ Seq(
    resolvers ++= commonResolvers,
    libraryDependencies ++= Dependencies.commonDeps,
    promptTheme := ScalapenosTheme,
    ScalariformKeys.preferences :=
      ScalariformKeys.preferences.value
        .setPreference(AlignParameters, true) //改行したときの引数の位置合わせ
        .setPreference(AlignSingleLineCaseStatements, true) //match-caseの"=>"の位置合わせ
        .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 30) //match-caseの"=>"の位置合わせの最大幅
        .setPreference(CompactStringConcatenation, false) //文字列結合の"+"の前後にスペースを入れるか
        .setPreference(DoubleIndentClassDeclaration, true) //クラスのextendやwithなどをダブルインデント
        .setPreference(IndentSpaces, 2) //インデントのスペースの個数
        .setPreference(PreserveSpaceBeforeArguments, false) //引数の括弧の前のスペースを保持するか
        .setPreference(RewriteArrowSymbols, false) //"<-"を"←"に、"=>"を"⇒"に
        .setPreference(SpaceBeforeColon, false) //":"の前にスペースを入れるか `a: Int` => `a : Int`
        .setPreference(SpaceInsideBrackets, false) //"[]"の間にスペースを入れるか `Seq[String]` => `Seq[ String ]`
        .setPreference(SpaceInsideParentheses, false) //"()"の間にスペースを入れるか
        .setPreference(PreserveDanglingCloseParenthesis, true) //複数行にわたる引数の閉じ括弧")"の改行を保持するか
        .setPreference(IndentPackageBlocks, true) //パッケージブロック内をインデントするか
  )

  private def hinaProject(theName: String, theBase: String): Project = {
    Project(id = theName, base = file(theBase))
      .settings(name := theName)
      .settings(commonSettings: _*)
      .configs(IntegrationTest)
  }

  private def hinaSubProject(id: String): Project = {
    val theName = s"hina-$id"
    hinaProject(theName, s"modules/$theName")
  }

  val rootProject = hinaProject("root", ".")

  private def nonPlayProject(id: String): Project = hinaSubProject(id)
    .settings(distSettings: _*)
    .settings(
      // playのプロジェクトでは、自動生成されたroutesのコードに対して、意味のない警告が大量にでてしまうので設定しない
      scalacOptions in(Compile, compile) ++= Seq("-Ywarn-unused", "-Ywarn-unused-import"))

  def playProject(id: String): Project = hinaSubProject(id)
    .settings(
      libraryDependencies ++= Dependencies.playDeps)

  def libProject(id: String): Project = nonPlayProject(id)

  def actorProject(id: String): Project = nonPlayProject(id)
    .settings(
      connectInput in run := true,
      libraryDependencies ++= Dependencies.akkaDeps ++ Dependencies.cliDeps)

  val rootSettings = commonSettings ++ Seq(name := "root")
}
