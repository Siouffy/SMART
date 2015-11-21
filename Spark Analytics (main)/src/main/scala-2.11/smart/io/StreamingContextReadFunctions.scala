
package smart.util.io

import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream
import smart.app.SMARTStructures._
import smart.util.io.util.IOUtil

class StreamingContextReadFunctions (ssc: StreamingContext) {
  def kafkaStream            (kafkaTopicsSources: Set[KafkaTopics] ): DStream[String] = IOUtil.kafkaStream(ssc, kafkaTopicsSources)
  def rmqQueueStream         (rmqQueueSources: Set[RMQQueue]) : DStream[String] = IOUtil.rmqQueueStream(ssc, rmqQueueSources)
  def rmqExchangeStream      (rmqExchangeSources: Set[RMQExchange]) : DStream[String] = IOUtil.rmqExchangeStream(ssc, rmqExchangeSources)
}

object StreamingContextReadFunctions {
  implicit def addIOFunctions(ssc: StreamingContext) = new StreamingContextReadFunctions(ssc)
}