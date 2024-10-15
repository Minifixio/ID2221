val scala3Version = "3.5.1"

lazy val root = project
  .in(file("."))
  .settings(
    name := "ID2221",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := "2.11.8",

    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-core" % "2.2.1",
      "org.apache.spark" %% "spark-graphx" % "2.2.1",
      "org.apache.spark" %% "spark-sql" % "2.2.1",
      "com.lihaoyi" %% "os-lib" % "0.9.0",
    ),
    
    // Add JVM options to fix the InaccessibleObjectException for java.net.URI
    javaOptions ++= Seq(
      "--add-opens=java.base/java.net=ALL-UNNAMED",
      "--add-opens=java.base/java.lang=ALL-UNNAMED",
      "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
    )
  )
