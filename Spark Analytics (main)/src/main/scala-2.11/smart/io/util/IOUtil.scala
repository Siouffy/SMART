package smart.util.io.util

import java.util.Properties
import smart.util.io.util._
import kafka.serializer.StringDecoder
import com.rabbitmq.client.MessageProperties
import com.stratio.receiver._
import com.tresata.spark.kafka._
import kafka.producer.ProducerConfig
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.kafka.KafkaUtils
import smart.app.SMARTStructures._

object IOUtil {
  /********************************************************************************************************************/
  /*if a topic doesn't exit, it is created .. the #partitions and #replicationFactor are set to the number of brokers
  * start consuming from the latest offset of each Kafka partition*/
  def kafkaStream (ssc: StreamingContext, kafkaTopicsSources: Set[KafkaTopics] ): DStream[String] = {
    /* In the Kafka parameters, we need to specify either metadata.broker.list or bootstrap.servers.
    By default, it will start consuming from the latest offset of each Kafka partition.
    If you set configuration auto.offset.reset in Kafka parameters to smallest,
    then it will start consuming from the smallest offset.
    */
    var kafkaStreams = Set[DStream[String]]()
    val kafkaTopicsSourcesIterator = kafkaTopicsSources.iterator
    while (kafkaTopicsSourcesIterator.hasNext) {
      val kafkaTopicsSource = kafkaTopicsSourcesIterator.next()
      /**/
      var zkConnect = ""
      kafkaTopicsSource.zkHostsToPortsMap.foreach(t =>  zkConnect+= t._1+":"+t._2+",")
      zkConnect = zkConnect.substring(0, zkConnect.size-1)
      /**/
      var topicsList = List[String]()
      kafkaTopicsSource.topics.foreach(s => topicsList ::= s)
      KafkaUtil.createKafkaTopics(zkConnect, topicsList , kafkaTopicsSource.brokerHostsToPortsMap.size, kafkaTopicsSource.brokerHostsToPortsMap.size)
      /**/
      var brokers = ""
      kafkaTopicsSource.brokerHostsToPortsMap.foreach(t=> brokers+= t._1+":"+t._2+",")
      brokers = brokers.substring(0, brokers.size-1)
      /**/
      val kafkaParams = Map[String, String]("metadata.broker.list" -> brokers)
      //kafkaTopic.topics.toSet[String].foreach(println(_))
      kafkaStreams += KafkaUtils.createDirectStream [String, String, StringDecoder, StringDecoder](ssc, kafkaParams, kafkaTopicsSource.topics.toSet).map(t=>t._2)
    }
    ssc.union(kafkaStreams.toSeq)
  }

  def rmqQueueStream (ssc: StreamingContext, rmqQueueSources: Set[RMQQueue]) : DStream[String] = {

    var rmqQueueStreams = Set[DStream[String]]()

    val rmqQueueSourcesIterator = rmqQueueSources.iterator
    while (rmqQueueSourcesIterator.hasNext) {
      val rmqQueueSource = rmqQueueSourcesIterator.next()
      val anyHost = rmqQueueSource.hostsToPortsMap.keysIterator.next()
      val anyHostPort = rmqQueueSource.hostsToPortsMap.get(anyHost)
      /*in case the queue is not there yet*/
      RabbitMQUtil.createQueue(anyHost, anyHostPort.get, rmqQueueSource.queueName)
      rmqQueueStreams += RabbitMQUtils.createStreamFromAQueue(ssc, anyHost, anyHostPort.get, rmqQueueSource.queueName, StorageLevel.MEMORY_AND_DISK)
    }
    ssc.union(rmqQueueStreams.toSeq)
  }

  def rmqExchangeStream (ssc: StreamingContext, rmqExchangeSources: Set[RMQExchange]) : DStream[String] = {

    var rmqExchangesStreams = Set[DStream[String]]()
    val rmqExchangesSourcesIterator = rmqExchangeSources.iterator
    while (rmqExchangesSourcesIterator.hasNext) {
      val rmqExchangeSource = rmqExchangesSourcesIterator.next()
      val anyHost = rmqExchangeSource.hostsToPortsMap.keysIterator.next()
      val anyHostPort = rmqExchangeSource.hostsToPortsMap.get(anyHost)

      if (rmqExchangeSource.exchangeType == "direct"){
        RabbitMQUtil.createExchange(anyHost, anyHostPort.get, "direct", rmqExchangeSource.exchangeName, false)
        rmqExchangesStreams += RabbitMQUtils.createStreamFromRoutingKeys(ssc, anyHost, anyHostPort.get, rmqExchangeSource.exchangeName, rmqExchangeSource.routingKeys.toSeq,StorageLevel.MEMORY_AND_DISK)
      }
      else if (rmqExchangeSource.exchangeType == "topic"){
        val queueName=RabbitMQUtil.createExchangeQueue(anyHost, anyHostPort.get, rmqExchangeSource.exchangeName, "topic", rmqExchangeSource.routingKeys.toArray)
        rmqExchangesStreams += RabbitMQUtils.createStreamFromAQueue(ssc, anyHost, anyHostPort.get, queueName, StorageLevel.MEMORY_AND_DISK)
      }
    }
    ssc.union(rmqExchangesStreams.toSeq)
  }

  /********************************************************************************************************************/

  def writeStreamToKafkaTopics (dStream: DStream[String], kafkaTopicsSink: KafkaTopics): Unit ={
    /*The KafkaRDD companion object contains methods writeWithKeysToKafka and writeToKafka, which can be used to write an
    RDD to Kafka. For this you will need to provide (besides the RDD itself) the Kafka topic and a ProducerConfig.
    The ProducerConfig will need to have metadata.broker.list and probably also serializer.class.*/

    val properties: Properties = new Properties
    var brokerList = ""
    kafkaTopicsSink.brokerHostsToPortsMap.foreach(t=> brokerList+= t._1+":"+t._2+",")
    brokerList = brokerList.substring(0, brokerList.size-1)

    properties.put("metadata.broker.list", brokerList)
    properties.put("serializer.class", "kafka.serializer.StringEncoder")
    val producerConfig: ProducerConfig = new ProducerConfig(properties)

    kafkaTopicsSink.topics.foreach(topic => dStream.foreachRDD(rdd => KafkaRDD.writeToKafka[String](rdd, topic, producerConfig)))
    //dStream.foreachRDD(rdd => KafkaRDD.writeToKafka[String](rdd, kafkaTopicsSink.topics.toList.head, producerConfig))
  }

  def writeStreamToRMQQueue (dStream: DStream[String], rmqQueueSink: RMQQueue): Unit = {
    val anyHost = rmqQueueSink.hostsToPortsMap.keysIterator.next()
    val anyHostPort = rmqQueueSink.hostsToPortsMap.get(anyHost)
    dStream.foreachRDD(rdd => {
      rdd.foreachPartition(partition => {
        val conn = RabbitMQUtil.connect(anyHost, anyHostPort.get)
        val channel = conn.createChannel()
        val durableQueue = false
        channel.queueDeclare(rmqQueueSink.queueName, durableQueue, false, false, null);
        val durableMessage = false

        partition.foreach(record => {
          if (durableMessage)
            channel.basicPublish("", rmqQueueSink.queueName, MessageProperties.PERSISTENT_TEXT_PLAIN, record.getBytes())
          else
            channel.basicPublish("", rmqQueueSink.queueName, null, record.getBytes())
        })
        channel.close();
        conn.close();
      })
    })
  }

  def writeStreamToRMQExchange (dStream: DStream[String], rmqExchangeSink: RMQExchange): Unit = {

    val anyHost = rmqExchangeSink.hostsToPortsMap.keysIterator.next()
    val anyHostPort = rmqExchangeSink.hostsToPortsMap.get(anyHost)

    dStream.foreachRDD(rdd => {
      rdd.foreachPartition(partition => {
        val conn = RabbitMQUtil.connect(anyHost, anyHostPort.get)
        val channel = conn.createChannel()
        val durableExchange = false ;
        channel.exchangeDeclare(rmqExchangeSink.exchangeName, rmqExchangeSink.exchangeType, durableExchange);

        val durableMessage = false
        partition.foreach(record => {
          if (durableMessage)
            rmqExchangeSink.routingKeys.foreach(routingKey => channel.basicPublish(rmqExchangeSink.exchangeName, routingKey, MessageProperties.PERSISTENT_TEXT_PLAIN, record.getBytes()))
            //channel.basicPublish(rmqExchangeSink.exchangeName, rmqExchangeSink.routingKeys.toList.head, MessageProperties.PERSISTENT_TEXT_PLAIN, record.getBytes())
          else
            rmqExchangeSink.routingKeys.foreach(routingKey =>channel.basicPublish(rmqExchangeSink.exchangeName, routingKey, null, record.getBytes()))
            //channel.basicPublish(rmqExchangeSink.exchangeName, rmqExchangeSink.routingKeys.toList.head, null, record.getBytes())
        })
        channel.close()
        conn.close()
      })
    })
  }

  /********************************************************************************************************************/

}