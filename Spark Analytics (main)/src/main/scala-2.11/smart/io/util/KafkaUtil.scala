package smart.util.io.util

import java.util.Properties
import kafka.admin.AdminUtils
import kafka.producer.{KeyedMessage, ProducerConfig}
import kafka.utils.{ZKStringSerializer, ZkUtils}
import org.I0Itec.zkclient.ZkClient
import org.apache.spark.streaming.dstream.DStream



object KafkaUtil {

  //bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 3 --partitions 1 --topic my-replicated-topic
  def createKafkaTopic (zkString: String = "localhost:2181", topicName: String, numPartitions: Int, replicationFactor: Int): Unit = {
    val sessionTimeoutMs = 10000
    val connectionTimeoutMs = 10000
    val zkClient = new ZkClient(zkString, sessionTimeoutMs, connectionTimeoutMs, ZKStringSerializer)
    val topicConfig = new Properties
    val existingTopics = ZkUtils.getAllTopics(zkClient)
    /**/
    if (existingTopics.contains(topicName))
      println(this.getClass.getName, "kafka topic " + topicName+" already exists")
    else{
      AdminUtils.createTopic(zkClient, topicName, numPartitions, replicationFactor, topicConfig)
      println(this.getClass.getName, "kafka topic " + topicName+" created")
    }
  }

  def createKafkaTopics (zkString: String = "localhost:2181", topics: List[String], partitionsPerTopic: Int, replicationFactor: Int): Unit ={
    val sessionTimeoutMs = 10000
    val connectionTimeoutMs = 10000
    val zkClient = new ZkClient(zkString, sessionTimeoutMs, connectionTimeoutMs, ZKStringSerializer)
    val topicConfig = new Properties
    val existingTopics = ZkUtils.getAllTopics(zkClient)
    /**/
    val topicsIterator = topics.iterator
    while (topicsIterator.hasNext){
      val topicName = topicsIterator.next()
      if (existingTopics.contains(topicName))
        println(this.getClass.getName, "kafka topic " + topicName+" already exists")
      else {
        AdminUtils.createTopic(zkClient, topicName, partitionsPerTopic, replicationFactor, topicConfig)
        println(this.getClass.getName, "kafka topic " + topicName + " created")
      }
    }
  }

  def sendToKafkaTopic(brokerList: String = "localhost:9092", topic: String, key: String, msg: String) {
    val properties: Properties = new Properties
    properties.put("metadata.broker.list", brokerList)
    properties.put("serializer.class", "kafka.serializer.StringEncoder")
    val producerConfig: ProducerConfig = new ProducerConfig(properties)
    val producer: kafka.javaapi.producer.Producer[String, String] = new kafka.javaapi.producer.Producer[String, String](producerConfig)
    val message: KeyedMessage[String, String] = new KeyedMessage[String, String](topic, key, msg)
    producer.send(message)
    producer.close
  }

  private var producer: kafka.javaapi.producer.Producer[String, String] = null

  private def getProducer (brokerList: String): kafka.javaapi.producer.Producer[String, String] = {

    if (producer == null){
      println("creating producer for the first time")
      val properties: Properties = new Properties
      properties.put("metadata.broker.list", brokerList)
      properties.put("serializer.class", "kafka.serializer.StringEncoder")
      val producerConfig: ProducerConfig = new ProducerConfig(properties)
      producer = new kafka.javaapi.producer.Producer[String, String](producerConfig)
    }

    else
      println("reusing existing producer")

    producer
  }

  def writeStreamToKafkaTopic(brokerList: String, topic:String, key: String, dStream: DStream[String]): Unit ={
    dStream.foreachRDD(rdd=> {
      rdd.foreachPartition(partition => {
        val producer = getProducer(brokerList)
        val keyAtWorker = key
        partition.foreach(record => {
          val message: KeyedMessage[String, String] = new KeyedMessage[String, String](topic, keyAtWorker, record)
          producer.send(message)
        })
        //producer.close
      })
    })
  }



}