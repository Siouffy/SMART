package test

import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream
import smart.app.SMARTFunctions


object TCPStreamReceiver {

  def streamInTestState(ssc: StreamingContext, port: Int): DStream[(String, Long, Double)] = {
    ssc.socketTextStream("localhost", port).map(SMARTFunctions.sensorDataParsingFunction)
  }

  def streamInTestQueryRequests(ssc: StreamingContext, port: Int): DStream[String] = {
    ssc.socketTextStream("localhost", port)
  }

}
