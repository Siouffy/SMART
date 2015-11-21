import sbt.ExclusionRule

name := "SparkAnalytics"

organization := "com.rugdsdev"

version := "1.0"

scalaVersion := "2.11.7"

/**********************************************************************************************************************/

libraryDependencies += "org.apache.spark" % "spark-core_2.11" % "1.4.1" % "provided"

libraryDependencies += "org.apache.spark" % "spark-streaming_2.11" % "1.4.1" % "provided"

libraryDependencies += "org.apache.spark" % "spark-mllib_2.11" % "1.4.1" % "provided"

/*in case we accssess an hdfs*/
libraryDependencies += "org.apache.hadoop" % "hadoop-client" % "2.4.0" % "provided"

/*more efficient machine learning*/
libraryDependencies += "org.jblas" % "jblas" % "1.2.4"
/**********************************************************************************************************************/
libraryDependencies += "org.apache.spark" % "spark-streaming-kafka_2.11" % "1.4.1"

libraryDependencies += "org.apache.kafka" % "kafka_2.11" % "0.8.2.0"

/* compatibility: 1.0.0 - 80% , 1.1.0 - 82% , 1.2.0 - 100% , 1.3.0 - 97% .. upgraded for spark 1.4.0 and kafka 0.8.2.0*/
libraryDependencies += "com.tresata" % "spark-kafka_2.11" % "0.4.0"
/**********************************************************************************************************************/

libraryDependencies += "com.rabbitmq" % "amqp-client" % "3.5.3"

/*compatibility: 1.0.0 - 52% , 1.1.0 - 54% , 1.2.0 - 92% , 1.3.0 - 100%*/
libraryDependencies += "com.stratio.receiver" % "rabbitmq" % "0.1.0-RELEASE" excludeAll(
  ExclusionRule(organization = "org.spark-project.akka"),
  ExclusionRule(organization = "org.apache.spark"),
  ExclusionRule(organization = "org.json4s"),
  ExclusionRule(organization = "com.twitter"),
  ExclusionRule(organization = "com.fasterxml.jackson.module")
  )
/**********************************************************************************************************************/

libraryDependencies += "com.datastax.spark" % "spark-cassandra-connector_2.11" % "1.4.0-M1"
/**********************************************************************************************************************/

/*dependencies for parsing JSON*/
libraryDependencies += "net.liftweb" % "lift-json_2.11" % "3.0-M5-1"

libraryDependencies += "net.ceedubs" % "ficus_2.11" % "1.1.2"


/* All of these dependencies of Stratio RMQ had some versioning problems
Error:Error while importing SBT project:
[error] Conflicting cross-version suffixes in:

:akka-remote
org.spark-project.akka:akka-slf4j
org.spark-project.akka:akka-actor
:spark-network-shuffle
org.apache.spark:spark-streaming
org.apache.spark:spark-core
org.apache.spark:spark-network-common
:json4s-ast
org.json4s:json4s-jackson
org.json4s:json4s-core
com.twitter:chill
:jackson-module-scala
*/

/*Lightning Required Dependencies*/
libraryDependencies += "org.json4s" %% "json4s-native" % "3.2.9"
libraryDependencies += "org.scalaj" %% "scalaj-http" % "1.1.4"



mainClass in assembly := Some("smart.SMARTLauncher")

assemblyJarName in assembly := "smart.jar"

test in assembly := {}


assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
//  case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
//  case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
//  case "application.conf"                            => MergeStrategy.concat
//  case "unwanted.txt"                                => MergeStrategy.discard
  case x => MergeStrategy.first

/*
 *case x => val oldStrategy = (assemblyMergeStrategy in assembly).value
 * oldStrategy(x)
 **/
}
