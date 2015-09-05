addSbtPlugin("com.typesafe.sbt"  % "sbt-git"               % "0.8.5")
addSbtPlugin("de.heikoseeberger" % "sbt-header"            % "1.5.0")
addSbtPlugin("io.spray"          % "sbt-revolver"          % "0.8.0-RC1")
addSbtPlugin("org.scalariform"   % "sbt-scalariform"       % "1.5.0")
addSbtPlugin("org.scalastyle"    % "scalastyle-sbt-plugin" % "0.7.0")
addSbtPlugin("org.scoverage"     % "sbt-scoverage"         % "1.3.1")

// Temporary workaround until https://github.com/scoverage/sbt-scoverage/issues/125 is fixed:
resolvers += Resolver.url("scoverage-bintray", url("https://dl.bintray.com/sksamuel/sbt-plugins/"))(Resolver.ivyStylePatterns)
// Temporary workaround until sbt-revolver 0.8.0 has been published to sbt/sbt-plugin-releases
resolvers += Resolver.url("jrudolph-sbt-plugins", url("https://dl.bintray.com/jrudolph/sbt-plugins"))(Resolver.ivyStylePatterns)
