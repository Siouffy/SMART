package smart.app

import org.apache.spark.SparkContext
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.{LabeledPoint, StreamingLinearRegressionWithSGD}
import org.apache.spark.streaming.dstream.DStream
import org.viz.lightning.Visualization
import smart.app.SMARTRuntime._


object SMARTStructures {

  /********************************************************************************************************************/
  /*DATA STRUCTURES REPRESENTING THE USERS INPUTS: SETTING A TARGET ENVIRONMENT AND CONFIGURING THE APPLICATION*/
  /********************************************************************************************************************/

  case class Latency (streaming: Int, learning: Int)
  /*
  * The application operates based on two kinds of latencies.
  *
  * streaming latency: Latency for which the application stream in and updates the environments state (2) persist coming
  * sensor readings on cassandra (3) accept and processes query requests from the users. The system also updates the
  * environments state visualizations based on that latency.
  *
  * learning latency: The Latency for training the prediction problems. This should be multiples of the prediction latency
  * (since we apply a window operation based on that latency). It shall also be large enough w.r.t the average
  * rate of change of the goal variables. i.e. if the learning latency is set very low, then we have the risk of resulting
  * very little (or even zero) number of training examples per batch.
  * */

  case class EnvironmentVariable(name: String, minValue: Double, maxValue: Double)
  case class EnvironmentAttribute(name: String, minValue: Double, maxValue: Double)
  case class EnvironmentSettings(environmentName: String, environmentVariables: Set[EnvironmentVariable], environmentAttributes: Set[EnvironmentAttribute], sampling: Int)
  /*
  * A target environment is modeled by specifying a name, set of environment variables, set of environment attributes
  * and a regular sampling interval.
  *
  * Environment Variables: represents an environment characteristic, which changes by time ex. room_temp.
  * Environment Attributes: represents an environment characteristic that is constant over time. These are very important
  * in preserving the identity of different instances of a target environment and also affects the reasoning about the
  * behaviour of goal variables in those different instances. (ex. room_insulation_material: in a room with high isolation
  * from the outside, the room_temp will not be very dependent on th external temperature, but a room that is highly
  * exposed to the outside, the room_temp will be highly affected by the external temperature
  *
  * */

  case class EnvironmenInstance(id: Int, sensorMappings: Map[String, String], attributeValues: Map[String, Double])
  /*
  * While the EnvironmentSettings defines an abstract model for the target environment, an environment instance is an
  * instantiation of a target environment. An environment instance shall correspond to a physical instance in real world.
  * Each environment instance shall have a unique id, and at least a single sensor for each environment variable that
  * periodically send its readings to the system (via one of its input mechanisms). When defining an environment instance
  * the user must specify values for the constant environment attributes.
  * */

  /**/
  case class CrossValidationSettings(nBatches: Int, maxError: Double)
  case class PredictionProblemSettings (regressionType: String, normalizeFeatureVectors: Boolean, initialWeights: List[Double], intercept: Double)
  case class PredictionProblem(name: String, goalVariable: String, explanatoryVariables: List[String], problemSettings: PredictionProblemSettings, crossValidationSettings: CrossValidationSettings)

  /**/
  case class CassandraPersistence(hostsToPortsMap: Map[String, Int], keyspaceName: String, tableName: String, create: Boolean)
  case class LightningVisualization(host: String, port: Int, lastBatchAccuracyVis: Map[String, List[VisualizationEntity]], lastBatchNTrainingExamplesVis: Map[String, List[VisualizationEntity]], environmentStateVis: Map[String, List[VisualizationEntity]])
  case class VisualizationEntity (name: String, color: Array[Double])


  /**/
  case class RMQQueue (hostsToPortsMap: Map[String, Int], queueName: String, operation: String)
  case class RMQExchange (hostsToPortsMap: Map[String, Int], exchangeName: String, exchangeType: String,routingKeys: Set[String], operation: String)
  case class KafkaTopics (zkHostsToPortsMap: Map[String, Int], brokerHostsToPortsMap: Map[String, Int], topics: Set[String], operation: String)
  case class IOSettings(rmqQueues: Set[RMQQueue], rmqExchanges: Set[RMQExchange], kafkaTopics: Set[KafkaTopics])



  /**/
  case class ApplicationSettings (appName : String,
                                  masterURL: String,
                                  debug : Boolean,
                                  latency: Latency,
                                  environmentSettings: EnvironmentSettings,
                                  environmentInstances: Set[EnvironmenInstance],
                                  predictionProblems: Map[String, PredictionProblem],
                                  cassandraPersistence: CassandraPersistence,
                                  lightningVisualization: LightningVisualization,
                                  iOSettings: IOSettings)

  /********************************************************************************************************************/
  /********************************************************************************************************************/



  /* a settings structure that is created from the user input [structures above] and broadcasted to Spark workers.
   * This structure encapsulates all the constant data representing the users settings that are required by the spark
   * workers to perform their job*/

  class SettingsBroadcast  (sc: SparkContext,
                           environmentVariables: Map[String, EnvironmentVariable],
                           environmentAttributes: Map[String, EnvironmentAttribute],

                           sensorIdToVariableNameMap : Map [String, String],
                           sensorIdToEnvironmentInstanceIdMap : Map [String, Int],
                           environmentInstanceIdToAttributeValuesMap : Map [Int, Map[String, Double]],

                           predictionProblemsSettings: Map[String, PredictionProblemSettings],
                           goalVariableSensorIdToNonGoalVariablesSensorIdsMap : Map [String, Set[String]],
                           problemNameToExplanatoryVariablesMap : Map[String, Map[String, Int]],
                           problemNameToGoalVariableMap : Map[String, String],

                           instanceIdToLightningVisualizationsMap : Map [Int, Map[String, Visualization]]) {

    val environmentVariablesBC = sc.broadcast(environmentVariables)
    val environmentAttributesBC = sc.broadcast(environmentAttributes)
    /**/
    val sensorIdToVariableNameMapBC = sc.broadcast(sensorIdToVariableNameMap)
    val sensorIdToEnvironmentInstanceIdMapBC = sc.broadcast(sensorIdToEnvironmentInstanceIdMap)
    val environmentInstanceIdToAttributeValuesMapBC = sc.broadcast(environmentInstanceIdToAttributeValuesMap)
    /**/
    val predictionProblemsSettingsBC = sc.broadcast(predictionProblemsSettings)
    val goalVariableSensorIdToNonGoalVariablesSensorIdsMapBC = sc.broadcast(goalVariableSensorIdToNonGoalVariablesSensorIdsMap)
    val problemNameToExplanatoryVariablesMapBC = sc.broadcast(problemNameToExplanatoryVariablesMap)
    val problemNameToGoalVariableMapBC = sc.broadcast(problemNameToGoalVariableMap)
    /**/
    val instanceIdToLightningVisualizationsMapBC = sc.broadcast(instanceIdToLightningVisualizationsMap)
  }


  class PredictionProblemLearningData extends Serializable () {
    
    var problemName : String = null
    var learningModel : StreamingLinearRegressionWithSGD = null
    var lastNBatchesError : Array[Double] = null
    private var currentBatchId : Int = -1
    var nTrainingExamples : Int = 0

    def this (problemName: String) {
      this()
      this.problemName = problemName
      initLearningModel
      initLastNBatchesError
    }

    private def initLearningModel  = {
      val predictionProblem = SMARTRuntime.predictionProblems(problemName)
      /*+2 to compensate goalVariableFromValue and goalVariableToValue*/
      var nFeatures = predictionProblem.explanatoryVariables.size + 2
      if (predictionProblem.problemSettings.regressionType == "polinomial")
        nFeatures += ((nFeatures*(nFeatures+1))/2)

      var modelWeights = Vectors.dense(predictionProblem.problemSettings.initialWeights.toArray)
      if (modelWeights.size != nFeatures)
        modelWeights = Vectors.zeros(nFeatures)

      learningModel = new StreamingLinearRegressionWithSGD()
        .setInitialWeights(modelWeights)
        .setStepSize(0.5)//midway in updating the parameters (i.e. not very biased)
        .setMiniBatchFraction(1.0) //take the whole batch
        .setNumIterations(1) //online learning
      /*normally intercept will remain 0 */
      //learningModel.algorithm.setIntercept(true)
      /*need to set the intercept also !!*/

      val debugText = "initializing "+problemName+" to: "+ learningModel.latestModel()+", "+learningModel.latestModel().weights
      SMARTLogManager.log(getClass.getName, debugText)
    }



    /*we keep track of the error information of the last nBatches ... prediction is only possible if each of the last
    nBatches have a error < what is defined by the user input*/
    private def initLastNBatchesError  = {
      val predictionProblem = SMARTRuntime.predictionProblems(problemName)
      lastNBatchesError = (new Array[Double](predictionProblem.crossValidationSettings.nBatches)).map(v=>1000.0)
    }

    def setCurrentBatchError (error: Double) : Unit = {
      /*updating the currentBatchId*/
      val maxValue = SMARTRuntime.predictionProblems(problemName).crossValidationSettings.nBatches
      currentBatchId = (currentBatchId+1) % maxValue
      lastNBatchesError(currentBatchId) = error
    }

    def getCurrentBatchError : Double = {
      if (currentBatchId != -1)
        lastNBatchesError(currentBatchId)
      else
        -1000
    }

    def increaseTrainingExamples (n:Int) : Unit = {
      nTrainingExamples += n
    }

    def canUseModelInPrediction : Boolean =  {
      val maxError = SMARTRuntime.predictionProblems(problemName).crossValidationSettings.maxError
      var res = true
      /*we can use the model in prediction only if the error for the last NBatches (separately) is < maxError*/
      lastNBatchesError.foreach(error => if (error > maxError) res = false)
      res
    }

    /**/

    def trainOn (ds : DStream[LabeledPoint]) : Unit = {
      learningModel.trainOn(ds)
    }

    def predictOn (ds: DStream[LabeledPoint]) : DStream[(Double, Double)] = {
      ds.transform(rdd => {
        rdd.map( labeledPoint => {
          val prediction = learningModel.latestModel().predict(labeledPoint.features)
          (labeledPoint.label, prediction)
        })
      })
    }

  }

































}