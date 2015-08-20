val reactiveFlows = project
  .in(file("."))
  .enablePlugins(AutomateHeaderPlugin, GitVersioning)

organization    := "de.heikoseeberger"
name            := "reactive-flows"
git.baseVersion := "0.1.0"
licenses        += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

scalaVersion   := "2.11.7"
scalacOptions ++= List(
  "-unchecked",
  "-deprecation",
  "-language:_",
  "-target:jvm-1.8",
  "-encoding", "UTF-8"
)

unmanagedSourceDirectories.in(Compile) := List(scalaSource.in(Compile).value)
unmanagedSourceDirectories.in(Test)    := List(scalaSource.in(Test).value)

val akkaVersion       = "2.4.0-RC2"
val akkaHttpVersion   = "1.0"
libraryDependencies ++= List(
  "com.typesafe.akka"        %% "akka-actor"                        % akkaVersion,
  "com.typesafe.akka"        %% "akka-http-experimental"            % akkaHttpVersion,
  "com.typesafe.akka"        %% "akka-http-spray-json-experimental" % akkaHttpVersion,
  "de.heikoseeberger"        %% "akka-log4j"                        % "0.3.2",
  "de.heikoseeberger"        %% "akka-sse"                          % "1.1.0",
  "org.apache.logging.log4j" %  "log4j-core"                        % "2.3",
  "com.typesafe.akka"        %% "akka-http-testkit-experimental"    % akkaHttpVersion % "test",
  "com.typesafe.akka"        %% "akka-testkit"                      % akkaVersion     % "test",
  "org.scalatest"            %% "scalatest"                         % "2.2.5"         % "test"
)

testFrameworks := List(sbt.TestFrameworks.ScalaTest)

initialCommands := """|import de.heikoseeberger.reactiveflows._""".stripMargin

import scalariform.formatter.preferences._
preferences := preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
  .setPreference(DoubleIndentClassDeclaration, true)

import de.heikoseeberger.sbtheader.license.Apache2_0
HeaderPlugin.autoImport.headers := Map("scala" -> Apache2_0("2015", "Heiko Seeberger"))

test.in(Test)         := { scalastyle.in(Compile).toTask("").value; test.in(Test).value }
scalastyleFailOnError := true

coverageMinimum          := 100
coverageFailOnMinimum    := true
coverageExcludedPackages := ".*App"

addCommandAlias("rf1", "reStart -Dakka.remote.netty.tcp.port=2551 -Dreactive-flows.http-service.port=9001")
addCommandAlias("rf2", "run     -Dakka.remote.netty.tcp.port=2552 -Dreactive-flows.http-service.port=9002")
addCommandAlias("rf3", "run     -Dakka.remote.netty.tcp.port=2553 -Dreactive-flows.http-service.port=9003")
