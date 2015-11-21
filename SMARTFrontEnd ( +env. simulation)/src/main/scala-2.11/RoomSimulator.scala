package simulation

import com.rabbitmq.client.{AMQP, Envelope, DefaultConsumer, ConnectionFactory}
import io.{RabbitMQUtil, KafkaUtil}
import net.liftweb.json.DefaultFormats
import net.liftweb.json.Serialization._

case class QueryRequest(qid: Int, problemName: String, teId: Int, stateOverride: Map[String, Double], target: Int)
case class QueryResponse(qid: Int, response: Double)

object RoomSimulator {

  val minute = 500

  def main (args: Array[String]) {
    //sendEnvironmentStateToRMQExchange("state_exchange", "a.b.c", new Room(300, "S12", "S23", "S34", "T45"))
    //sendEnvironmentStateToRMQExchange("state_exchange", "a.b.d", new Room(400, "S11", "S22", "S33", "T44"))

    //sendEnvironmentStateToKafkaTopic()
    //sendPredictionRequests(1111)

    sendPredictionRequestsToRMQQueue("request_queue")
    rmqConsumer("response_queue")
  }


  def sendEnvironmentStateToKafkaTopic (topic: String) : Unit = {
    val thread = new Thread {
      override def run: Unit = {

        val room1: Room = new Room(300, "S12", "S23", "S34", "T45")
        val zkConnect = "10.0.0.11:2181"
        val brokerList = "10.0.0.12:9092"
        KafkaUtil.createKafkaTopics(zkConnect, List("topic1", "topic2"), 1,1)
        /*****************************************/
        /*****************************************/
        println("sending state to Kafka @ zk:["+zkConnect+"] & brokers:["+brokerList+"]")
        while (true) {
          /*1sec = 1000, 0.25 sec = 1000 == minute in real time*/
          Thread.sleep(minute)
          room1.simulateMinute()
          room1.sendStateToKafkaTopic(brokerList, topic)
        }
      }
    }
    thread.start
  }

  def sendEnvironmentStateToRMQQueue (queueName: String) : Unit = {
    val thread = new Thread {
      override def run: Unit = {

        val room1: Room = new Room(300, "S12", "S23", "S34", "T45")
        val host = "10.0.0.21"
        val port = 5672
        /*****************************************/
        /*****************************************/
        println("sending state to RMQ @ "+host+":"+port)
        while (true) {
          /*1sec = 1000, 0.25 sec = 1000 == minute in real time*/
          Thread.sleep(minute)
          room1.simulateMinute()
          room1.sendStateToRMQQueue(host, port, queueName)
        }
      }
    }
    thread.start
  }

  def sendEnvironmentStateToRMQExchange (exchangeName: String, routingKey: String, room: Room) : Unit = {
    val thread = new Thread {
      override def run: Unit = {
        val host = "10.0.0.21"
        val port = 5672
        /*****************************************/
        /*****************************************/
        println("sending state to RMQ @ "+host+":"+port)
        while (true) {
          /*1sec = 1000, 0.25 sec = 1000 == minute in real time*/
          Thread.sleep(minute)
          room.simulateMinute()
          room.sendStateToRMQExchange(host, port, exchangeName, routingKey)
        }
      }
    }
    thread.start
  }

  def sendPredictionRequestsToRMQQueue (queueName : String) : Unit = {
    val thread = new Thread {
      override def run: Unit = {
        implicit val formats = DefaultFormats
        val host = "10.0.0.21"
        val port = 5672
        while (true) {
          val ln = readLine()
          val qr1 = QueryRequest((Math.random() * 10000).toInt, "room_heating_time_prediction_n_p", 300, Map("room_Temp" -> 10), ln.toInt)
          val qr2 = QueryRequest((Math.random() * 10000).toInt, "room_heating_time_prediction_n_l", 300, Map("room_Temp" -> 10), ln.toInt)
          val requestString1 = write(qr1)
          val requestString2 = write(qr2)
          println(requestString1)
          println(requestString2)
          println("****")
          RabbitMQUtil.produceToQueue(host, port, queueName, requestString1)
          RabbitMQUtil.produceToQueue(host, port, queueName, requestString2)
        }
      }
    }
    thread.start
  }


  def rmqConsumer(queueName: String): Unit = {

    val thread = new Thread {
      override def run: Unit = {
        val factory = new ConnectionFactory()
        factory.setHost("10.0.0.21")
        val connection = factory.newConnection()
        val channel = connection.createChannel()
        channel.queueDeclare(queueName, false, false, false, null)

        val consumer = new DefaultConsumer(channel) {
          override def handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte]) {
            val message = new String(body, "UTF-8")
            println(" [x] Received '" + message + "'")
          }
        }
        channel.basicConsume(queueName, true, consumer)
      }
    }
    thread.start()
  }



}