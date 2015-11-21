/*
import api.io.util.IOUtil.{KafkaTopicSource, RMQExchangeSource}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.dstream.DStream

import api.io.StreamingContextIOFunctions._
import api.io.DStreamsWriteFunctions._

object IOTestApp {

  val rMQTopicExchangeDest = RMQExchangeSource(Map("10.0.0.3" -> 5672), "topicExchange", "topic", Set("aa.bb.cc"))
  val rMQTopicExchangeSource = RMQExchangeSource(Map("10.0.0.3" -> 5672), "topicExchange", "topic", Set("aa.#"))
  val kafkaTopicSource = KafkaTopicSource(Map("10.0.0.1" -> 2181), Map("10.0.0.2" -> 9092), Set("topic1"))
  val kafkaTopicDest = KafkaTopicSource(Map("10.0.0.1" -> 2181), Map("10.0.0.2" -> 9092), Set("topic1"))

  def main (args: Array[String]) {
    setLogLevels()
    println("Initializing Spark Core and Streaming")

    val inargs = Array("IOTestApp", "local[*]")
    val conf = new SparkConf()
      .setAppName(inargs(0))
      .setMaster(inargs(1))
    val ssc = new StreamingContext(conf, Seconds(5))
    /******************************************************************************************************************/
    println("Generating Test DStream")
    val toWriteStream = generateRandomStream(ssc)
    /******************************************************************************************************************/
    println("STARTING TEST")
    toWriteStream.writeStreamToKafkaTopic(kafkaTopicDest)
    toWriteStream.writeStreamToRMQExchange(rMQTopicExchangeDest)

    val st1 = ssc.kafkaStream(Set(kafkaTopicSource))
    st1.print()
    st1.count().print()

    val st2 = ssc.rmqExchangeStream(Set(rMQTopicExchangeSource))
    st2.print()
    st2.count().print()
    /******************************************************************************************************************/
    ssc.start()
    ssc.awaitTermination()
  }





  def generateRandomStream(ssc: StreamingContext): DStream[String] = {
    var randomRDDQueue = scala.collection.mutable.Queue[RDD[String]]()
    for (j <- 0 to 3) {
      var randList = List[String]()
      for (i <- 0 to 1000)
        randList ::= "This is test string number [" + j + "] [" + i + "]"
      val rdd = ssc.sparkContext.makeRDD(randList)
      randomRDDQueue += rdd
    }
    ssc.queueStream(randomRDDQueue)
  }


  def setLogLevels() {
    val log4jInitialized = Logger.getRootLogger.getAllAppenders.hasMoreElements
    if (!log4jInitialized) {
      // We first log something to initialize Spark's default logging, then we override the logging level.
      //logInfo("Setting log level to [ERROR] for org, akka, spark and streaming")
      Logger.getRootLogger.setLevel(Level.ERROR)
      Logger.getLogger("org").setLevel(Level.ERROR)
      Logger.getLogger("akka").setLevel(Level.ERROR)
      Logger.getLogger("streaming").setLevel(Level.ERROR)
      Logger.getLogger("spark").setLevel(Level.ERROR)
    }
  }


}

*/