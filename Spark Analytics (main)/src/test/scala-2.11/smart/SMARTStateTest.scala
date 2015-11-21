package smart.modules

import org.apache.spark.SparkConf
import org.apache.spark.streaming.{StreamingContext, Seconds}
import smart.app.{SMARTLogManager, SMARTRuntime}
import smart.app.modules.SMARTState
import test.TCPStreamReceiver


object SMARTStateTest {
  def main(args: Array[String]) {
    SMARTRuntime.start()
    SMARTLogManager.setLogLevels()
    /********************************/
    val conf = new SparkConf()
    .setMaster("local[3]")
    .setAppName("TestApp")
    val ssc = new StreamingContext(conf, Seconds(SMARTRuntime.latency.streaming))


    SMARTRuntime.initBroadcastData(ssc.sparkContext)

    ssc.checkpoint("/home/elsioufy/cpdir")
    /********************************/
    val sensorRecordingStream = TCPStreamReceiver.streamInTestState(ssc, 9999)
    sensorRecordingStream.print()
    /********************************/
    SMARTState.persistSensorDataStream(sensorRecordingStream)
    val environmentState = SMARTState.updateEnvironmentState(sensorRecordingStream)
    SMARTState.visualizeEnvironmentsState(environmentState)
    /********************************/
    ssc.start()
    ssc.awaitTermination()
  }
}