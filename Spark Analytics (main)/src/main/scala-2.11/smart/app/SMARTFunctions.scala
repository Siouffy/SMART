package smart.app

import api.{SamplingInterval, ResamplingFunctions}
import com.google.common.io.BaseEncoding
import net.liftweb.json
import net.liftweb.json.Serialization
import net.liftweb.json.DefaultFormats
import org.apache.spark.mllib.regression.LabeledPoint


object SMARTFunctions {

  case class EncodedSensorDataItem(timestamp: Long, process_id: String, sensor_id: String, instance_id: String, value: String)
  case class SensorDataItem(timestamp: Long, process_id: String, sensor_id: String, instance_id: String, value: Double)
  case class QueryRequest(qid: Int, problemName: String, teId: Int, stateOverride: Map[String, Double], target: Int)
  case class QueryResponse(qid: Int, response: Double)
  /********************************************************************************************************************/
  /********************************************************************************************************************/
  /*takes a json string in sensor format and returns it as (sensorID, timestamp, value)*/
  private def parseSensorData(sensorDataJSONString: String): (String, Long, Double) = {
    implicit val formats = DefaultFormats
    val parsedJSON = json.parse(sensorDataJSONString)
    val encodedSensorData = parsedJSON.extract[EncodedSensorDataItem]
    val sensorData = SensorDataItem(encodedSensorData.timestamp, encodedSensorData.process_id, encodedSensorData.sensor_id,
      encodedSensorData.instance_id, (new String(BaseEncoding.base64().decode(encodedSensorData.value), 0)).toDouble)
    (sensorData.sensor_id, sensorData.timestamp, sensorData.value)
  }
  val sensorDataParsingFunction = (sensorDataJSONString: String) => parseSensorData(sensorDataJSONString)

  /*takes a json string in the prediction format and returns it as (instanceID, (qid, problemName, stateOverrideMap, target))*/
  private def parseQueryRequest(queryRequestJSONString: String): (Int, (Int, String, Map[String, Double], Int)) = {
    implicit val formats = DefaultFormats
    val parsedJSON = json.parse(queryRequestJSONString).extract[QueryRequest]
    (parsedJSON.teId, (parsedJSON.qid, parsedJSON.problemName, parsedJSON.stateOverride, parsedJSON.target))
  }
  val queryRequestParsingFunction = (queryRequestJSONString: String) => parseQueryRequest(queryRequestJSONString)

  private def parseQueryResponse(queryResponse: QueryResponse): String = {
    implicit val formats = DefaultFormats
    Serialization.write(queryResponse)
  }
  val queryResponseParsingFunction = (queryResponse: QueryResponse) => parseQueryResponse(queryResponse)
  /********************************************************************************************************************/
  /********************************************************************************************************************/

  /*in our application we prefer to work on [int] data representing the goal variables ... */
  def toClosestInt(value: Double): Double = {
    val diff = value - value.toInt
    if (diff > 0.5)
      value.toInt + 1
    else
      value.toInt
  }
  val valueMappingFunction = (value: Double) => toClosestInt(value)
  /** ******************************************************************************************************************/
  /** ******************************************************************************************************************/
  val samplingFunction = (timestamp: Long, samplingInterval: SamplingInterval) => ResamplingFunctions.sampleAccurate(timestamp, samplingInterval)
  val upsamplingFunction = (timestamp: Long, currentSamplingInterval: SamplingInterval) => ResamplingFunctions.upsampleAccurate(timestamp, currentSamplingInterval)
  val downsamplingFunction = (timestamp: Long, samplingInterval: SamplingInterval) => ResamplingFunctions.downsample(timestamp, samplingInterval)
  /** ******************************************************************************************************************/
  /** ******************************************************************************************************************/

  def toPolynomialFeatures(featuresVector: Array[Double]): Array[Double] = {
    /*maps a features vector into a polinomial features vector of degree2 x,y,z => x,y,z,x2,xy,xz,y2,yz, z2*/
    val nFeatures = featuresVector.size
    val addedFeatures = ((nFeatures) * (nFeatures + 1)) / 2
    val labelledFeaturesVector = new Array[Double](nFeatures + addedFeatures)

    var idx = 0
    for (idx <- 0 until nFeatures)
      labelledFeaturesVector(idx) = featuresVector(idx)
    idx = nFeatures

    var i = 0
    var j = 0
    (0 until nFeatures).map(i => {
      (i until nFeatures).map(j => {
        labelledFeaturesVector(idx) = featuresVector(i) * featuresVector(j)
        idx += 1
      })
    })

    labelledFeaturesVector
  }

  /*stub function for validating created feature vectors ... to be replaced with some real validation function*/
  /*[problemName, LabeledPoint]*/
  def validate (t : Tuple2[String, LabeledPoint]) : Boolean = {
    t._2.label >3
  }

}