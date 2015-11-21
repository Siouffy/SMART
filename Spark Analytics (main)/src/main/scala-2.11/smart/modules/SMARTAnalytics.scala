package smart.app.modules

import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.streaming.dstream.DStream
import smart.app.SMARTFunctions.QueryResponse
import smart.app.{SMARTRuntime, SMARTFunctions}
import smart.app.SMARTStructures.SettingsBroadcast


object SMARTAnalytics {

  def processUserQueries(queryRequestsStream: DStream[(Int, (Int, String, Map[String, Double], Int))], environmentsStateStream: DStream[(Int, Map[String, (Long, Double)])]): DStream[String] = {
    /*map for discarding the timestamps ...*/
    val initPredictionStream = environmentsStateStream.mapValues(instanceStateMap => instanceStateMap.mapValues(t => t._2))
      .join(queryRequestsStream)
      /*creating feature-vectors out of the environments state and the users inputs*/
      /*a single request could be mapped to multiple requests since prediction can be only done from gv to  gv +- 1*/

    if (SMARTRuntime.debug)
      initPredictionStream.map{ case (instanceId, (instanceStateMap, (qid, problemName, stateOverrideMap, target_))) => {
        val problemExplanatoryVariablesMap = SMARTRuntime.settingsBroadcast.problemNameToExplanatoryVariablesMapBC.value(problemName)
        val problemGoalVariable = SMARTRuntime.settingsBroadcast.problemNameToGoalVariableMapBC.value(problemName)
        val gvTo = problemGoalVariable+"_target"
        val queryMap = instanceStateMap ++ stateOverrideMap ++  Map(gvTo->target_.toDouble)
        ("recieved query ==> qid: "+ qid +", instanceId: "+instanceId+", problemName: "+problemName+", updatedFeaturesMap: "+ queryMap.toArray.mkString(" "))
    }}.print()

    val predictionStream = initPredictionStream
      .flatMap { case (instanceId, (instanceStateMap, (qid, problemName, stateOverrideMap, target_))) => {
      var target = target_.toDouble
      /*needed to create the features vector to be predicted*/
      val problemExplanatoryVariablesMap = SMARTRuntime.settingsBroadcast.problemNameToExplanatoryVariablesMapBC.value(problemName)
      val problemGoalVariable = SMARTRuntime.settingsBroadcast.problemNameToGoalVariableMapBC.value(problemName)
      val problemSettings = SMARTRuntime.settingsBroadcast.predictionProblemsSettingsBC.value(problemName)
      var nFeatures = problemExplanatoryVariablesMap.size + 2
      /*we override the current environments state with the inputs by the user)*/
      val queryMap = instanceStateMap ++ stateOverrideMap

      /**remember: we only predict +/- 1**/
      var fromValue = SMARTFunctions.valueMappingFunction(queryMap(problemGoalVariable)) /*to make sure it is an int*/
      var increment : Int = 0
      if (target > fromValue) increment = 1
      else if (target < fromValue) increment = -1
      var toValue = fromValue + increment
      /****************************************************************************************************************/
      /*i.e. not defined by the user*/
      if (target < 0){
        target = fromValue +1
        increment = 1
        toValue = target
      }

      var res = List[(Int, String, Array[Double])]()
      while (fromValue != target) {
        var featuresVector = new Array[Double](nFeatures)

        /*including the non goal variables in the features vector - [taking into account normalization]*/
        queryMap.foreach(kv => {
          if (problemExplanatoryVariablesMap.contains(kv._1)) {
            featuresVector(problemExplanatoryVariablesMap(kv._1)) = kv._2
            if (problemSettings.normalizeFeatureVectors) {
              val min = SMARTRuntime.settingsBroadcast.environmentVariablesBC.value(kv._1).minValue
              val max = SMARTRuntime.settingsBroadcast.environmentVariablesBC.value(kv._1).maxValue
              val normalizedValue = (kv._2 - min) / (max - min)
              featuresVector(problemExplanatoryVariablesMap(kv._1)) = normalizedValue
            }
          }
        })
        /*including attribtues in the feature vector*/
        SMARTRuntime.settingsBroadcast.environmentInstanceIdToAttributeValuesMapBC.value(instanceId).foreach { case (attrName, attValue) => {
          if (problemExplanatoryVariablesMap.contains(attrName)) {
            featuresVector(problemExplanatoryVariablesMap(attrName)) = attValue
            if (problemSettings.normalizeFeatureVectors) {
              val min = SMARTRuntime.settingsBroadcast.environmentAttributesBC.value(attrName).minValue
              val max = SMARTRuntime.settingsBroadcast.environmentAttributesBC.value(attrName).maxValue
              val normalizedValue = (attValue - min) / (max - min)
              featuresVector(problemExplanatoryVariablesMap(attrName)) = normalizedValue
            }
          }
        }
        }
        featuresVector(problemExplanatoryVariablesMap.size) = fromValue
        featuresVector(problemExplanatoryVariablesMap.size + 1) = toValue
        if (problemSettings.normalizeFeatureVectors) {
          val min = SMARTRuntime.settingsBroadcast.environmentVariablesBC.value(problemGoalVariable).minValue
          val max = SMARTRuntime.settingsBroadcast.environmentVariablesBC.value(problemGoalVariable).maxValue
          val normalizedFromValue = (fromValue - min) / (max - min)
          val normalizedToValue = (toValue - min) / (max - min)
          featuresVector(problemExplanatoryVariablesMap.size) = normalizedFromValue
          featuresVector(problemExplanatoryVariablesMap.size + 1) = normalizedToValue
        }
        /******/
        if (problemSettings.regressionType == "polinomial")
          featuresVector = SMARTFunctions.toPolynomialFeatures(featuresVector)

        res ::= (qid, problemName, featuresVector)
        fromValue = toValue
        toValue = fromValue + increment
      }
      res
    }}
      /*prediction*/
      .transform(rdd =>
      rdd.map{ case (qid, problemName, featuresVector) => {
        if (!SMARTRuntime.problemNameToProblemLearningDataMap(problemName).canUseModelInPrediction)
          (qid, -1000.toDouble)
        else {
          val prediction = SMARTRuntime.problemNameToProblemLearningDataMap(problemName).learningModel.latestModel().predict(Vectors.dense(featuresVector))
          (qid, prediction)
        }
      }}
      )
      .reduceByKey( (a,b) => a+b)
      .map(t => QueryResponse(t._1, t._2))
    predictionStream.map(SMARTFunctions.queryResponseParsingFunction)
  }


}
