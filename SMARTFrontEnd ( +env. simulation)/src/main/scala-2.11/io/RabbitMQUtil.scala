package io

import com.rabbitmq.client._


object RabbitMQUtil {


  def connect (host: String, port: Int): Connection = {
    val factory = new ConnectionFactory()
    //      factory.setUsername(userName); [ok]
    //      factory.setPassword(password); [ok]
    //      factory.setVirtualHost(virtualHost); ??
    factory.setHost(host)
    factory.setPort(port)
    factory.newConnection();
  }

  /*produces directly to a given queue name using the default '' exchange
  * if queue doesn't exist creates a non-durable, non-exclusive, no-auto-delete queue with the same name*/
  def produceToQueue (host: String, port: Int, queueName: String, message: String): Unit = {

    val durableQueue = false /*passed in the queue declaration*/
    val durableMessage = false /*passed ot the publish function */;

    val conn = connect(host, port)
    val channel = conn.createChannel();

    /*idempotent operation*/ /*durable = durableQueue, exclusive = false, auto delete = false*/
    channel.queueDeclare(queueName, durableQueue, false, false, null);

    if (durableMessage)
      /*mark our messages as persistent*/
      /*Note: The persistence guarantees aren't strong,If a stronger guarantee is needed, we can use publisher confirms.*/
      /*exchange_name, routing_key = 'queuename', prop, message*/
      channel.basicPublish("", queueName, MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes());
    else
      channel.basicPublish("", queueName, null, message.getBytes());

    channel.close();
    conn.close();
    //println("Sent: " + message+" to queue: "+ queueName);
  }

  /*FANOUT EXCHANGE::: a message goes to all queues that are bound to the fanout exchange*/
  /*if exchange doesn't exist, creates a non-durable exchange*/
  def produceToFanoutExchange (host: String, port: Int, exchangeName: String, message: String){
    val durableMessage = false  /*passed to the publish function */
    val durableExchange = false

    val conn = connect(host, port);
    val channel = conn.createChannel();

    /*we don't declare a queue, because we don't really care*/
    channel.exchangeDeclare(exchangeName, "fanout", durableExchange);

    if (durableMessage)
      /*exchange_name, routing_key = 'queuename', prop, message*/
      channel.basicPublish(exchangeName, "", MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes());

    else
      channel.basicPublish(exchangeName, "", null, message.getBytes());

    channel.close();
    conn.close();
    //println("Sent: " + message + "to fanout exchange: " + exchangeName);
  }

  /*DIRECT EXCHANGE ::: a message goes to the queues whose binding key exactly matches the routing key of the message.*/
  /*if the exchange doesn't exist, creates a non-durable exchange*/
  def produceToDirectExchange(host: String, port: Int, exchangeName: String, routingKey: String, message: String) {

    val durableMessage = false
    val durableExchange = false

    val conn = connect(host, port)
    val channel = conn.createChannel()

    channel.exchangeDeclare(exchangeName, "direct", durableExchange);

    if (durableMessage)
      channel.basicPublish(exchangeName, routingKey, MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes());
    else
      channel.basicPublish(exchangeName, routingKey, null, message.getBytes());

    channel.close();
    conn.close();

    //println("Sent: " + message + "to direct exchange: " + exchangeName+" with routing key: " + routingKey);
  }

  def produceToTopicExchange(host: String, port: Int, exchangeName: String, routingKey: String, message: String) {

    val durableMessage = false
    val durableExchange = false

    val conn = connect(host, port)
    val channel = conn.createChannel()
    channel.exchangeDeclare(exchangeName, "topic", durableExchange);

    if (durableMessage)
      channel.basicPublish(exchangeName, routingKey, MessageProperties.PERSISTENT_TEXT_PLAIN, message.getBytes());
    else
      channel.basicPublish(exchangeName, routingKey, null, message.getBytes());

    channel.close();
    conn.close();

    //println("Sent: " + message + "to topic exchange: " + exchangeName+" with routing key: " + routingKey);
  }

  /********************************************************************************************************************/
  /********************************************************************************************************************/

  /* if queue doesn't exist creates a non-durable, non-exclusive, no-auto-delete queue unless specified*/
  def createQueue (host: String, port: Int, queueName: String, durable: Boolean = false , exclusive: Boolean = false, autoDelete: Boolean = false) {
    val conn = connect(host, port)
    val channel = conn.createChannel()
    channel.queueDeclare(queueName, durable, exclusive, autoDelete, null)
    channel.close()
    conn.close()
    System.out.println("queue created: " + queueName)
  }

  /*if exchange doesn't exist, creates a non-durable exchange unless specified*/
  def createExchange (host: String, port: Int, exType: String, exchangeName: String, durable: Boolean){

    val conn = connect(host,port)
    val channel = conn.createChannel()
    channel.exchangeDeclare(exchangeName, exType, durable);
    channel.close();
    conn.close();
    //println("created "+ exType + " exchange: " + exchangeName);
  }

  /*creates a new queue and bind it to the exchange based on the routing key.. returns the queue name created*/
  /*if the exchange doesn't exist creates a non durable exchange*/
  def createExchangeQueue (host: String, port: Int, exchangeName: String, exchangeType: String, bindingKeys: Array[String]) : String = {
    val conn = connect(host,port)
    val channel = conn.createChannel()
    channel.exchangeDeclare(exchangeName, exchangeType , false)

    channel.queueDeclare(exchangeName+"Queue", false, false, false,null)
    val queueName = exchangeName+"Queue"

    /*probably that's a fanout setting*/
    if (bindingKeys.length == 0)
      channel.queueBind(queueName, exchangeName, "")

    else
      bindingKeys.foreach(bk => channel.queueBind(queueName, exchangeName, bk))

    channel.close();
    conn.close();
    queueName
  }


  /********************************************************************************************************************/
  /********************************************************************************************************************/

}
