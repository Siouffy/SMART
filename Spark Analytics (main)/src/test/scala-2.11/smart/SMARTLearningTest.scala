package smart.modules

import org.apache.spark.SparkConf
import org.apache.spark.streaming.{Seconds, StreamingContext}
import smart.app.{SMARTLogManager, SMARTRuntime}
import smart.app.modules.{SMARTLearning, SMARTState}
import test.TCPStreamReceiver


object SMARTLearningTest {
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
    //sensorRecordingStream.print()
    /********************************/
    val featureVectors =
      SMARTLearning.createFeatureVectorsStream(sensorRecordingStream)
    val pbAccStream = SMARTLearning.trainAndEstimateProblemsState(featureVectors)
    pbAccStream.print()
    SMARTLearning.visualizeProblemsState(pbAccStream)
    /********************************/
    ssc.start()
    ssc.awaitTermination()
  }
}
