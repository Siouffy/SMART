package smart.app.modules

import api.SamplingInterval
import api.rdds.{SampledTimeStampedValueRDD, TimeStampedTransitionsRDD, TimeStampedValueRDD}
import api.rdds.TimeStampedValueRDDFunctions._
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.Seconds
import org.apache.spark.streaming.dstream.DStream
import smart.app.{SMARTLogManager, SMARTFunctions, SMARTRuntime}
import smart.app.SMARTStructures.SettingsBroadcast

object SMARTLearning {

  def createFeatureVectorsStream (sensorDataStream: DStream[(String, Long, Double)]) : DStream[(String, LabeledPoint)] = {

    sensorDataStream.window(Seconds(SMARTRuntime.latency.learning), Seconds(SMARTRuntime.latency.learning))
      .transform(rdd =>getGoalVariableTransitionToNonGoalVariablesTransitionAverages(rdd)) /*goalVariableTransitionToNonGoalVariablesTransitionAverages: (goalVariableSensorId, goalVariableTransition, Iterable(ngvSensorID, ngvTransitionAverage))*/
      .persist(StorageLevel.MEMORY_AND_DISK) //DStream[(String, ((Long, Double), (Long,Double)), Iterable[(String, Double)])]
      .flatMap(in=> extractInitialFeatureVectors(in)) /*initialFeaturesVector: (String, Double, Array[Double])*/
      .map(in => customizeFeatureVector(in)) /*features vector: considers problem settings: currently only regression type*/
  }

  private def getGoalVariableTransitionToNonGoalVariablesTransitionAverages(rdd: RDD[(String, Long, Double)]): RDD[(String, ((Long, Double), (Long, Double)), Iterable[(String, Double)])] = {
    if (rdd.isEmpty()) null /*not required, just for testing: we assume that each sensor will send atleast a single data recording [per learning latency]*/
    /*determining the min and max timestamp values for the current batch*/
    val timestampsRDD = rdd.map(t => t._2)
    val minTimeStamp = timestampsRDD.min
    val maxTimeStamp = timestampsRDD.max
    SMARTLogManager.log(getClass.getName, "processing environments state received a long the period [" + minTimeStamp + " , " + maxTimeStamp+"]", true)
    /** **************************************************************************************************************/
    /*considering mapping the sensor reading values ex. 5.5786786 -> 6 ..etc*/
    val mappedRdd = rdd.map(t => (t._1, t._2, SMARTFunctions.valueMappingFunction(t._3)))
    val environmentVariablesRDDMap = splitRDDBySensorId(mappedRdd, new SamplingInterval(minTimeStamp, maxTimeStamp, SMARTRuntime.environmentSettings.sampling), SMARTRuntime.settingsBroadcast.sensorIdToVariableNameMapBC.value.keySet) //Map[String, TimeStampedValueRDD]
    /*Each SampledTimeStampedRDD will possibly be used multiple times. We persist each RDD so that it is not recomputed every time it is used*/
    val sampledEnvironmentVariablesRDDMap = environmentVariablesRDDMap.mapValues(rdd => rdd.toSampledTimeStampedValueRDD(SMARTFunctions.downsamplingFunction, SMARTFunctions.upsamplingFunction).persist(StorageLevel.MEMORY_AND_DISK)) //Map[String, SampledTimeStampedValueRDD]
    /**/
    val goalVariablesRDDMap = sampledEnvironmentVariablesRDDMap.filterKeys(k => SMARTRuntime.settingsBroadcast.goalVariableSensorIdToNonGoalVariablesSensorIdsMapBC.value.contains(k)) //Map[String, SampledTimeStampedValueRDD]
    val goalVariablesTransitionsRDDMap = goalVariablesRDDMap.mapValues(_.findSuccessiveTransitions/*.persist(StorageLevel.MEMORY_AND_DISK)*/) //Map[String, TimeStampedTransitionsRDD]
    /**/
    val goalVariableTransitionToNonGoalVariablesTransitionAveragesMap = groupGoalVariablesAndNonGoalVariablesByTransitionAverages(goalVariablesTransitionsRDDMap, sampledEnvironmentVariablesRDDMap) //Map[String, RDD[(((Long, Double), (Long,Double)), Iterable[(String, Double)])]]
    val goalVariableTransitionToNonGoalVariablesTransitionAverages =
      SMARTUtil.union(goalVariableTransitionToNonGoalVariablesTransitionAveragesMap
        .map { case (goalVariableSensorId, rdd) => rdd.map(t => (goalVariableSensorId, t._1, t._2)) }.toSeq)
    goalVariableTransitionToNonGoalVariablesTransitionAverages
  }

  private def splitRDDBySensorId(rdd: RDD[(String, Long, Double)], samplingInterval: SamplingInterval, sensorIds: Set[String]): Map[String, TimeStampedValueRDD] = {
    var res = Map[String, RDD[(Long, Double)]]()
    sensorIds.foreach(sensorId => res += (sensorId -> rdd.filter(t => t._1 == sensorId).map(t => (t._2, t._3))))
    res.map { case (key, value) => {
      (key, value.toTimeStampedValueRDD(SMARTFunctions.samplingFunction, samplingInterval))
    }
    }
  }

  private def groupGoalVariablesAndNonGoalVariablesByTransitionAverages(goalVariablesTransitionsRDDMap: Map[String, TimeStampedTransitionsRDD], sampledEnvironmentVariablesRDDMap: Map[String, SampledTimeStampedValueRDD]): Map[String, RDD[(((Long, Double), (Long, Double)), Iterable[(String, Double)])]] = {
    val goalVariableNonGoalVariablesTransitionAveragesMap = goalVariablesTransitionsRDDMap.map { case (key, value) => {
      (key, value.findTransitionsAverage(sampledEnvironmentVariablesRDDMap.filter(t => SMARTRuntime.settingsBroadcast.goalVariableSensorIdToNonGoalVariablesSensorIdsMapBC.value(key).contains(t._1))))
    }}
    /*String -> String -> itr (transition, double) */
    goalVariableNonGoalVariablesTransitionAveragesMap.mapValues(m => {
      var res = Set[RDD[(((Long, Double), (Long, Double)), (String, Double))]]()
      m.foreach { case (key, value) => {
        res += value.map(t => (t._1, (key, t._2)))
      }}
      SMARTUtil.union(res.toSeq).groupByKey//
      //.persist(StorageLevel.MEMORY_AND_DISK) --> not needed!
    })
  }

  /**/
  private def extractInitialFeatureVectors(in: (String, ((Long, Double), (Long, Double)), Iterable[(String, Double)])): List[(String, Double, Array[Double])] = {
    /*each goal variable transition (that is for a single environment instance) shall be mapped to [n] feature vectors for [n] prediction problems that it represents*/
    val (gvSensorId, ((fromTimestamp, fromValue), (toTimestamp, toValue)), nonGoalVariableAverages) = in

    /*res (problemName, label, features)*/
    var res = List[(String, Double, Array[Double])]()

    /*the label for a prediction problem feature vector is the time difference in the goal variable value change*/
    val label = toTimestamp - fromTimestamp
    val goalVariableName = SMARTRuntime.settingsBroadcast.sensorIdToVariableNameMapBC.value(gvSensorId)

    SMARTRuntime.settingsBroadcast.problemNameToExplanatoryVariablesMapBC.value
      /*accessing all the problems that have that goalVariable*/
      .filter(t => SMARTRuntime.settingsBroadcast.problemNameToGoalVariableMapBC.value(t._1) == goalVariableName)
      .foreach { case (problemName, featureVectorMap) => {
      /* size --> includes explanatoryVariables (nongoal variables and attribtues) +2 to include from and to goal variable values values*/
      var featuresVector = new Array[Double](featureVectorMap.size + 2)

      nonGoalVariableAverages.foreach { case (ngvSensorId, ngvValue) => {
        val ngvName = SMARTRuntime.settingsBroadcast.sensorIdToVariableNameMapBC.value(ngvSensorId)
        if (featureVectorMap.keySet.contains(ngvName)) {
          featuresVector(featureVectorMap(ngvName)) = ngvValue
          if (SMARTRuntime.settingsBroadcast.predictionProblemsSettingsBC.value(problemName).normalizeFeatureVectors) {
            val ngvname = SMARTRuntime.settingsBroadcast.sensorIdToVariableNameMapBC.value(ngvSensorId)
            val min = SMARTRuntime.settingsBroadcast.environmentVariablesBC.value(ngvname).minValue
            val max = SMARTRuntime.settingsBroadcast.environmentVariablesBC.value(ngvname).maxValue
            val normalizedNGVValue = (ngvValue - min) / (max - min)
            featuresVector(featureVectorMap(ngvName)) = normalizedNGVValue
          }
        }
      }}

      var envInstanceId = SMARTRuntime.settingsBroadcast.sensorIdToEnvironmentInstanceIdMapBC.value(gvSensorId)
      SMARTRuntime.settingsBroadcast.environmentInstanceIdToAttributeValuesMapBC.value(envInstanceId).foreach { case (attrName, attValue) => {
        if (featureVectorMap.keySet.contains(attrName)) {
          featuresVector(featureVectorMap(attrName)) = attValue
          if (SMARTRuntime.settingsBroadcast.predictionProblemsSettingsBC.value(problemName).normalizeFeatureVectors) {
            val min = SMARTRuntime.settingsBroadcast.environmentAttributesBC.value(attrName).minValue
            val max = SMARTRuntime.settingsBroadcast.environmentAttributesBC.value(attrName).maxValue
            val normalizedAttributeValue = (attValue - min) / (max - min)
            featuresVector(featureVectorMap(attrName)) = normalizedAttributeValue
          }
        }
      }}

      featuresVector(featureVectorMap.size) = fromValue
      featuresVector(featureVectorMap.size + 1) = toValue
      if (SMARTRuntime.settingsBroadcast.predictionProblemsSettingsBC.value(problemName).normalizeFeatureVectors) {
        val gvname = SMARTRuntime.settingsBroadcast.sensorIdToVariableNameMapBC.value(gvSensorId)
        val min = SMARTRuntime.settingsBroadcast.environmentVariablesBC.value(gvname).minValue
        val max = SMARTRuntime.settingsBroadcast.environmentVariablesBC.value(gvname).maxValue
        val normalizedFromValue = (fromValue - min) / (max - min)
        val normalizedToValue = (toValue - min) / (max - min)
        featuresVector(featureVectorMap.size) = normalizedFromValue
        featuresVector(featureVectorMap.size + 1) = normalizedToValue
      }

      res ::=(problemName, label.toDouble, featuresVector)
    }}
    res
  }

  private def customizeFeatureVector(in: (String, Double, Array[Double])): (String, LabeledPoint) = {
    val (problemName, label, initialFeaturesVector) = in
    var featuresVector = initialFeaturesVector
    if (SMARTRuntime.settingsBroadcast.predictionProblemsSettingsBC.value(problemName).regressionType == "polinomial")
      featuresVector = SMARTFunctions.toPolynomialFeatures(featuresVector)
    (problemName, new LabeledPoint(label, Vectors.dense(featuresVector)))
  }


  /**/
  def trainAndEstimateProblemsState(featureVectorsStream: DStream[(String, LabeledPoint)]): DStream[(String, (Double, Long))] = {

    val predictionProblemsNames = SMARTRuntime.predictionProblems.keySet
    val validatedFeatureVectorsStream = featureVectorsStream.filter(SMARTFunctions.validate)

    val problemNameToFeatureVectorStreamMap = SMARTUtil.toDStreamsMapByKey[LabeledPoint](validatedFeatureVectorsStream, predictionProblemsNames)
    val problemNameToLastBatchInfoStream = SMARTRuntime.problemNameToProblemLearningDataMap.map { case (problemName, learningData) => {
      /*we do the prediciton before the training ... for more accurate cross validation. */
      val res = (problemName, learningData.predictOn(problemNameToFeatureVectorStreamMap(problemName)))
      learningData.trainOn(problemNameToFeatureVectorStreamMap(problemName))
      res
    }}
    val errorAndNTrainingExamplesStream = SMARTUtil.union(problemNameToLastBatchInfoStream.map{
      case (problemName, ds) => {
        val errorStream = ds.map{case (v, p) => math.abs(v - p)}.reduce(_+_).map(v => (problemName, v))
        val nTrainingExamplesStream = ds.count().map (v => (problemName,v))
        errorStream.join(nTrainingExamplesStream)}}.toSeq)

    val result = errorAndNTrainingExamplesStream.map{case (problemName, (mse, nTrainingExamples)) => (problemName, (mse/nTrainingExamples, nTrainingExamples))}
    result.foreachRDD(rdd => {
      rdd.foreach{case (problemName, (error, nTrainingExamples)) => {
        SMARTRuntime.problemNameToProblemLearningDataMap(problemName).increaseTrainingExamples(nTrainingExamples.toInt)
        SMARTRuntime.problemNameToProblemLearningDataMap(problemName).setCurrentBatchError(error)

        val problemStateDebugString = "Problem State Info: " +problemName+": last N batches error("+SMARTRuntime.problemNameToProblemLearningDataMap(problemName).lastNBatchesError.mkString(", ") +"), total # of training examples used in training " + SMARTRuntime.problemNameToProblemLearningDataMap(problemName).nTrainingExamples
        SMARTLogManager.log(getClass.getName, problemStateDebugString)
      }}
    })
    result
  }

  /*@MASTER*/
  def visualizeProblemsState (problemStateStream: DStream[(String, (Double, Long))]) : Unit = {

    problemStateStream.foreachRDD(problemAccuracyRDD => {
      val problemAccuracyMap = problemAccuracyRDD.collect().toMap
      SMARTRuntime.problemAccuracyLightningVisualizationsMap.foreach{case (visName, vis) => {
        val visEntitiesSize = SMARTRuntime.lightningVisualization.lastBatchAccuracyVis(visName).size
        val dataArray = new Array[Array[Double]](visEntitiesSize)
        var idx = 0
        val itr = SMARTRuntime.lightningVisualization.lastBatchAccuracyVis(visName).iterator
        while (itr.hasNext){
          val problemName = itr.next().name
          val lastBatchAccuracy = problemAccuracyMap(problemName)._1
          dataArray(idx) = Array(lastBatchAccuracy)
          idx += 1
        }
        vis.appendData(dataArray)
      }}

      SMARTRuntime.problemTrainingExamplesLightningVisualizationsMap .foreach{case (visName, vis) => {
        val visEntitiesSize = SMARTRuntime.lightningVisualization.lastBatchNTrainingExamplesVis (visName).size
        val dataArray = new Array[Array[Double]](visEntitiesSize)
        var idx = 0
        val itr = SMARTRuntime.lightningVisualization.lastBatchNTrainingExamplesVis(visName).iterator
        while (itr.hasNext){
          val problemName = itr.next().name
          val lastNTrainingExamplesAccuracy = problemAccuracyMap(problemName)._2
          dataArray(idx) = Array(lastNTrainingExamplesAccuracy)
          idx += 1
        }
        vis.appendData(dataArray)
      }}
    })

  }

}