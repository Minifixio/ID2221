val scala3Version = "3.5.1"

lazy val root = project
  .in(file("."))
  .settings(
    name := "ID2221",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.0.0" % Test,
      "org.apache.spark" %% "spark-core" % "2.2.1",
      "org.apache.spark" %% "spark-sql" % "2.2.1",
      "org.apache.spark" % "spark-graphx_2.11" % "2.2.1"
      )
  )
