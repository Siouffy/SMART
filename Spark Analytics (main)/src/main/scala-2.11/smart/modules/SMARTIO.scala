package smart.app.modules

import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream
import smart.app.{SMARTRuntime}
import smart.util.io.StreamingContextReadFunctions._
import smart.util.io.DStreamsWriteFunctions._

object SMARTIO {

  def streamIn(ssc: StreamingContext, streamKey: String): DStream[String] = {
    var kafkaTopicsStream : DStream[String] = null
    var RMQQueuesStream : DStream[String] = null
    var RMQExchangesStream : DStream[String] = null
    var streamList = List[DStream[String]] ()

    val kafkaTopics = SMARTRuntime.ioSettings.kafkaTopics.filter(kt => kt.operation == streamKey)
    if (kafkaTopics.nonEmpty) {
      kafkaTopicsStream = ssc.kafkaStream(kafkaTopics)
      streamList ::= kafkaTopicsStream
    }

    val rmqQueues = SMARTRuntime.ioSettings.rmqQueues.filter(q => q.operation == streamKey)
    if (rmqQueues.nonEmpty) {
      RMQQueuesStream = ssc.rmqQueueStream(rmqQueues)
      streamList ::= RMQQueuesStream
    }

    val rmqExchanges = SMARTRuntime.ioSettings.rmqExchanges.filter(e => e.operation == streamKey)
    if (rmqExchanges.nonEmpty) {
      RMQExchangesStream = ssc.rmqExchangeStream(rmqExchanges)
      streamList ::=RMQExchangesStream
    }
    ssc.union(streamList)
  }

  def streamOutQueryResponses(ssc: StreamingContext, queryResponsesStream: DStream[String]) : Unit = {
    val kafkaTopics = SMARTRuntime.ioSettings.kafkaTopics.filter(kt => kt.operation == "queryResponse")
    if (kafkaTopics.nonEmpty)
      kafkaTopics.foreach(kT => queryResponsesStream.writeToKafkaTopics(kT))

    val rmqQueues = SMARTRuntime.ioSettings.rmqQueues.filter(q => q.operation == "queryResponse")
    if (rmqQueues.nonEmpty)
      rmqQueues.foreach(rmqQ => queryResponsesStream.writeToRMQQueue(rmqQ))

    val rmqExchanges = SMARTRuntime.ioSettings.rmqExchanges.filter(e => e.operation == "queryResponse")
    if (rmqExchanges.nonEmpty)
      rmqExchanges.foreach(rmqE => queryResponsesStream.writeToRMQExchange(rmqE))
  }

}