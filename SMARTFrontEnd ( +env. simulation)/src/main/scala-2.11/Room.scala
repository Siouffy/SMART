package simulation

import java.io.{FileWriter, PrintWriter}
import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import io.{RabbitMQUtil, KafkaUtil}
import smart.util.io.util._
/********************************************/

class Room(id: Int, nOccupantsSID: String, extTempSID: String,heatingLevelSID: String, roomTempSID: String) {

  var nOccupants : Double = (Math.random()*29 + 1) .toInt
  var heatingLevel : Double = 4
  var extTemp : Double = 0
  var roomTemp: Double = 0
  var heatingMaxTempMap : Map[Double, Double] = Map[Double, Double](5.0->35.0, 4.0->30.0, 3.0->20.0, 2.0->10.0, 1.0->5.0)
  /*************************/
  var simulationMinutes = 1000


  private def heatingDecision () : Unit = {
    if(heatingLevel == 4 && Util.toClosestInt(roomTemp) >= heatingMaxTempMap(heatingLevel))
      heatingLevel = 2
    else if (heatingLevel == 2 && Util.toClosestInt(roomTemp) <= heatingMaxTempMap(heatingLevel))
      heatingLevel = 4
  }

  private def roomOccupantsDecision () : Unit = {
    /*room occupants decsion is taken each couple of minutes*/
    if (roomOccupantsMintsCounter >= roomOccupantsDecisionMinutes){
      roomOccupantsMintsCounter = 1
      /**/
      var newOccupants: Double = 0
      val r: Double = Math.random
      if (r >= 0.9) newOccupants = -10
      else if (r >= 0.6) newOccupants = -5
      else if (r >= 0.4) newOccupants = 0
      else if (r >= 0.1) newOccupants = 5
      else newOccupants = 10

      if(Math.random() > 0.8){
        if (newOccupants <1 && heatingLevel == 4)
          newOccupants *= -1
        if (newOccupants >1 && heatingLevel == 2)
          newOccupants *= -1
      }

      nOccupants += newOccupants
      if (nOccupants > roomOccupantsMax)
        nOccupants = roomOccupantsMax
      if (nOccupants < 0)
        nOccupants = 0
    }
    else
      roomOccupantsMintsCounter+=1
  }

  private def tempDecision () : Unit = {
    val preTemp = roomTemp.toInt

    val diff = extTemp - roomTemp

    if (heatingMaxTempMap(heatingLevel) > roomTemp) {
      roomTemp += 0.35
      roomTemp += (diff/150)
    }
    else {
      roomTemp += (diff / 50)
    }
    /************************************/
    /************************************/
    if (nOccupants <= 5)
      roomTemp += 0
    else if (nOccupants <= 10)
      roomTemp += 0.07
    else if (nOccupants <= 20)
      roomTemp += 0.13
    else if (nOccupants <=30)
      roomTemp += 0.18
    /************************************/
    /************************************/
    val afterTemp = roomTemp.toInt
  }

  private def tempTransition() : Unit = {
    /*transition occured*/
    if (Util.toClosestInt(preTemp) != Util.toClosestInt(roomTemp)){
      try
        fw.write(preMinutes+" "+Util.toClosestInt(preTemp) + " " + simulationMinutes + " " +Util.toClosestInt(roomTemp)+ " "+ (simulationMinutes-preMinutes) + "\n")
      finally
        fw.flush()

      preTemp = roomTemp
      preMinutes = simulationMinutes
    }
  }

  /**/

  def simulateMinute () : Unit = {
    tempDecision()
    heatingDecision()
    roomOccupantsDecision()
    simulationMinutes += 1
    tempTransition()
    printState()
  }

  def printState() : Unit = {
    var res = getState()
    println(res.values.mkString(" "))
  }

  def getState() : (Map[String, Double]) = {
    var res = Map[String, Double]()
    res += nOccupantsSID -> nOccupants
    res += heatingLevelSID -> heatingLevel
    res += extTempSID -> extTemp
    res += roomTempSID -> roomTemp
    res
  }

  def sendState(writer: PrintWriter) : Unit ={

    //val unix_timestamp = new Date().getTime / 1000
    val unix_timestamp = simulationMinutes

    getState().foreach( kv  => {
      if (Math.random() >= 0.7){
        val sid = kv._1
        val sval = kv._2
        val encodedSVal = BaseEncoding.base64().encode(sval.toInt.toString.getBytes(Charsets.UTF_8))

        if (sid.contains("T"))
          println("================>" + sid+" "+sval)
        else
          println(sid+" "+sval)

        val sensorData = "{ \"timestamp\":" + unix_timestamp + ", \"process_id\":\"" + "na" + "\", \"sensor_id\":\"" +sid + "\",\"instance_id\":\"" + "na" + "\",\"value\":\"" + encodedSVal + "\"}"
        writer.println(sensorData)
        writer.flush()
      }
    })
  }

  def sendStateToKafkaTopic (brokerList: String, topic: String) : Unit = {

    val unix_timestamp = simulationMinutes

    getState().foreach( kv  => {
      if (Math.random() >= 0.7){
        val sid = kv._1
        val sval = kv._2
        val encodedSVal = BaseEncoding.base64().encode(sval.toInt.toString.getBytes(Charsets.UTF_8))

        if (sid.contains("T"))
          println("================>" + sid+" "+sval)
        else
          println(sid+" "+sval)

        val sensorData = "{ \"timestamp\":" + unix_timestamp + ", \"process_id\":\"" + "na" + "\", \"sensor_id\":\"" +sid + "\",\"instance_id\":\"" + "na" + "\",\"value\":\"" + encodedSVal + "\"}"
        KafkaUtil.sendToKafkaTopic(brokerList, topic, null, sensorData)
      }
    })
  }

  def sendStateToRMQQueue (host: String, port: Int,  queueName: String) : Unit = {

    val unix_timestamp = simulationMinutes

    getState().foreach( kv  => {
      if (Math.random() >= 0.7){
        val sid = kv._1
        val sval = kv._2
        val encodedSVal = BaseEncoding.base64().encode(sval.toInt.toString.getBytes(Charsets.UTF_8))

        if (sid.contains("T"))
          println("================>" + sid+" "+sval)
        else
          println(sid+" "+sval)

        val sensorData = "{ \"timestamp\":" + unix_timestamp + ", \"process_id\":\"" + "na" + "\", \"sensor_id\":\"" +sid + "\",\"instance_id\":\"" + "na" + "\",\"value\":\"" + encodedSVal + "\"}"
        RabbitMQUtil.produceToQueue(host, port, queueName, sensorData)
      }
    })
  }

  def sendStateToRMQExchange (host: String, port: Int,  exchangeName: String, routingKey: String) : Unit = {

    val unix_timestamp = simulationMinutes
    getState().foreach( kv  => {
      if (Math.random() >= 0.7){
        val sid = kv._1
        val sval = kv._2
        val encodedSVal = BaseEncoding.base64().encode(sval.toInt.toString.getBytes(Charsets.UTF_8))

        if (sid.contains("T"))
          println("================>" + sid+" "+sval)
        else
          println(sid+" "+sval)

        val sensorData = "{ \"timestamp\":" + unix_timestamp + ", \"process_id\":\"" + "na" + "\", \"sensor_id\":\"" +sid + "\",\"instance_id\":\"" + "na" + "\",\"value\":\"" + encodedSVal + "\"}"
        RabbitMQUtil.produceToTopicExchange(host, port, exchangeName, routingKey, sensorData)
      }
    })
  }






  /*nOccupants Decision*/
  private var roomOccupantsMintsCounter = 1
  private val roomOccupantsDecisionMinutes = 5
  private val roomOccupantsMax = 30

  /*Room Transition*/
  var preTemp = roomTemp
  var preMinutes = simulationMinutes
  val fw = new FileWriter("room_" + id + "_transitions.txt", true)



}





