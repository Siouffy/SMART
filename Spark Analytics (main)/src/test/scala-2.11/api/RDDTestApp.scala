/*

object RDDTestApp {




  def main (args: Array[String]) {


    LogManager.setLogLevels()

    val inargs = Array("MyApp", "local[*]")
    val conf = new SparkConf()
      .setAppName(inargs(0))
      .setMaster(inargs(1))

    val sc = new SparkContext(conf)

    val samplingInterval = new SamplingInterval(11,27,2)
    val tempList = List[(Long, Double)] ((14,3),(15,4),(19,5),(21,5),(22,5))
    val tempRDD = sc.makeRDD(tempList)


    val samplingFunction = (timestamp: Long, samplingInterval: SamplingInterval) => ResamplingFunctions.sampleAccurate(timestamp, samplingInterval)
    val upsamplingFunction = (timestamp: Long, currentSamplingInterval: SamplingInterval) => ResamplingFunctions.upsampleAccurate(timestamp, currentSamplingInterval)
    val downsamplingFunction = (timestamp: Long, samplingInterval: SamplingInterval) => ResamplingFunctions.downsample(timestamp, samplingInterval)


    val tsvRdd = tempRDD.toTimeStampedValueRDD(samplingFunction, samplingInterval).print





    //tsvRdd.toSampledTimeStampedValueRDD(SMARTFunctions.samplingFunction, SMARTFunctions.upsamplingFunction).print

  }


}
*/
