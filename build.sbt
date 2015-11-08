name := "akka-s3"

version := "0.1"

scalaVersion := "2.11.7"

// Each test suite can be run in parallel
parallelExecution in Test := false

libraryDependencies ++= {
  val akkaStreamV = "2.0-M1"
  val akkaV = "2.3.12"

  Seq(
    "com.amazonaws" % "aws-java-sdk" % "1.10.12",
    "com.google.guava" % "guava" % "18.0",
    "commons-io" % "commons-io" % "2.4",
    "commons-logging" % "commons-logging" % "1.2",
    "commons-codec" % "commons-codec" % "1.10",
    "org.apache.httpcomponents" % "httpclient" % "4.5",
    "org.apache.httpcomponents" % "httpmime" % "4.5.1",
    "org.apache.tika" % "tika-core" % "1.10",
    "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-core-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-testkit-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-http-xml-experimental" % akkaStreamV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
    "com.typesafe.akka" %% "akka-slf4j" % akkaV,
    "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
    "org.scala-lang.modules" %% "scala-pickling" % "0.10.1",
    "org.scala-lang.modules" %% "scala-xml" % "1.0.2",
    "com.typesafe.slick" %% "slick" % "3.0.2",
    // "org.slf4j" % "slf4j-nop" % "1.6.4",
    "org.xerial" % "sqlite-jdbc" % "3.7.2",
    "ch.qos.logback" % "logback-classic" % "1.1.2",
    "com.github.scopt" %% "scopt" % "3.3.0"
  )
}

resolvers += Resolver.sonatypeRepo("public")
