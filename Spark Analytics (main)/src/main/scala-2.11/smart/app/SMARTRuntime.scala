package smart.app

/*configuration parsing*/
import com.typesafe.config.{ConfigObject, Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.spark.SparkContext

import scala.reflect.io.File

/*application structures*/
import smart.app.SMARTStructures._

import org.viz.lightning.Lightning
import org.viz.lightning.Visualization



object SMARTRuntime {

  /*user input*/
  var appName : String = null
  var masterURL : String = null
  var debug = true
  /*********************************/
  var latency : Latency = null
  var environmentSettings: EnvironmentSettings = null
  var environmentInstances = Set[EnvironmenInstance]()
  var predictionProblems = Map[String, PredictionProblem]()
  var cassandraPersistence : CassandraPersistence = null
  var lightningVisualization : LightningVisualization = null
  var ioSettings : IOSettings = null
  /*********************************/  /*********************************/

  /*learning data [Used only @ Driver]*/
  var problemNameToProblemLearningDataMap = Map[String, PredictionProblemLearningData] ()

  /*visualization data [Used only @ Driver]*/
  var problemAccuracyLightningVisualizationsMap = Map [String, Visualization] ()
  var problemTrainingExamplesLightningVisualizationsMap = Map [String, Visualization] ()

  /*broadcast data [Used @ Workers settingBroadcase.----.value]*/
  var settingsBroadcast: SettingsBroadcast = null


  def start(inputConfigPath: String = null, applicationSettings: ApplicationSettings = null) : Unit = {
    if (applicationSettings == null)
      parse(inputConfigPath)
    else
      parse(applicationSettings)

    initLearning
    initVisualizations

    SMARTLogManager.log(getClass.getName, "Cassandra @: " + cassandraPersistence.hostsToPortsMap.toArray.mkString(" "))
    SMARTLogManager.log(getClass.getName, "Lightning @: " + lightningVisualization.host+":"+lightningVisualization.port)
    ioSettings.rmqQueues.foreach(rmqQueue => {
      SMARTLogManager.log(getClass.getName, "RMQQueue @: " + rmqQueue.hostsToPortsMap.toArray.mkString(" ") +" for "+rmqQueue.operation)})
    ioSettings.rmqExchanges.foreach(rmqExchange => {
      SMARTLogManager.log(getClass.getName, "RMQExchange @: " + rmqExchange.hostsToPortsMap.toArray.mkString(" ") +" for "+rmqExchange.operation)})
    ioSettings.kafkaTopics.foreach(kafkaTopics => {
      SMARTLogManager.log(getClass.getName, "KafkaTopics @: [zk]"+ kafkaTopics.zkHostsToPortsMap.toArray.mkString(" ")
        +" and [brokers] " + kafkaTopics.brokerHostsToPortsMap.toArray.mkString(" ") +" for "+ kafkaTopics.operation)})
  }


  private def parse (inputConfigPath: String = null) : Unit = {

    var config : Config = null
    if (inputConfigPath !=null){
      SMARTLogManager.log(getClass.getName, "Parsing input configuration file")
      val configFile = new java.io.File(inputConfigPath)
      config = ConfigFactory.parseFile(configFile)
    }
    else {
      SMARTLogManager.log(getClass.getName, "Parsing default configuration file")
      config = ConfigFactory.load()
    }
    /******************************************************************************************************************/
    appName = config.as[String]("appName")
    masterURL = config.as[String]("masterURL")
    debug = config.as[Boolean] ("debug")
    latency = config.as[Latency]("latency")
    /**/
    val environmentName = config.as[String]("environmentSettings.environmentName")
    val environmentVariables = config.as[Set[EnvironmentVariable]]("environmentSettings.environmentVariables")
    val environmentAttributes = config.as[Set[EnvironmentAttribute]]("environmentSettings.environmentAttributes")
    val sampling = config.as[Int]("environmentSettings.sampling")
    environmentSettings = EnvironmentSettings(environmentName, environmentVariables, environmentAttributes, sampling)
    environmentInstances = config.as[Set[EnvironmenInstance]] ("environmentInstances")
    /**/
    predictionProblems = config.as[Set[PredictionProblem]]("predictionProblems").map(predictionProblem => (predictionProblem.name, predictionProblem)).toMap
    cassandraPersistence = config.as[CassandraPersistence]("cassandraPersistence")
    lightningVisualization = config.as[LightningVisualization]("lightningVisualization")
    /**/
    val rmqQueues = config.as[Set[RMQQueue]] ("ioSettings.rmqQueues")
    val rmqExchanges = config.as[Set[RMQExchange]] ("ioSettings.rmqExchanges")
    val kafkaTopics = config.as[Set[KafkaTopics]] ("ioSettings.kafkaTopics")
    ioSettings = IOSettings(rmqQueues, rmqExchanges, kafkaTopics)
    /**/
  }

  private def parse (applicationSettings: ApplicationSettings) : Unit = {
    SMARTLogManager.log(getClass.getName, "Parsing input ApplicationSettings")
    appName = applicationSettings.appName
    masterURL = applicationSettings.masterURL
    debug = applicationSettings.debug
    latency = applicationSettings.latency
    environmentSettings = applicationSettings.environmentSettings
    environmentInstances = applicationSettings.environmentInstances
    predictionProblems = applicationSettings.predictionProblems
    cassandraPersistence = applicationSettings.cassandraPersistence
    lightningVisualization = applicationSettings.lightningVisualization
    ioSettings = applicationSettings.iOSettings
  }


  /**/

  private def initLearning () : Unit = {
    SMARTLogManager.log(getClass.getName, "Initializing Learning Models ")
    predictionProblems.values.foreach(predictionProblem =>
      problemNameToProblemLearningDataMap += predictionProblem.name -> new PredictionProblemLearningData(predictionProblem.name))
  }

  private def initVisualizations (): Unit = {
    SMARTLogManager.log(getClass.getName, "Initializing Driver visualization objects")
    initPredictionProblemsAccuracyVisualizations
    initPredictionProblemsNTrainingExamplesVisualizations
  }

  private def initPredictionProblemsAccuracyVisualizations : Unit = {

    val predictionAccuracySession = Lightning("http://" + lightningVisualization.host + ":" + lightningVisualization.port)
    predictionAccuracySession.createSession("Prediction Accuracy")

    lightningVisualization.lastBatchAccuracyVis.foreach{case (visName, visEntities) => {

      val dataArray = new Array[Array[Double]] (visEntities.size)
      val sizeArray = new Array[Double] (visEntities.size)
      val colorArray = new Array[Array[Double]] (visEntities.size)
      
      for (i <-0 until visEntities.size){
        dataArray(i) = Array(0.0)
        sizeArray(i) = 10
        colorArray(i) = visEntities(i).color
      }
      /**/
      val vis = predictionAccuracySession.lineStreaming(dataArray, sizeArray, color = colorArray, xaxis = visName)
      /*this will be used for updating each visualzation*/
      problemAccuracyLightningVisualizationsMap  +=  visName -> vis
    }}
  }

  private def initPredictionProblemsNTrainingExamplesVisualizations : Unit = {

    val trainingExamplesAccuracySession = Lightning("http://" + lightningVisualization.host + ":" + lightningVisualization.port)
    trainingExamplesAccuracySession.createSession("N Training Examples")

    lightningVisualization.lastBatchNTrainingExamplesVis.foreach{case (visName, visEntities) => {
      val dataArray = new Array[Array[Double]] (visEntities.size)
      val sizeArray = new Array[Double] (visEntities.size)
      val colorArray = new Array[Array[Double]] (visEntities.size)

      for (i <-0 until visEntities.size ){
        dataArray(i) = Array(0.0)
        sizeArray(i) = 10
        colorArray(i) = visEntities(i).color
      }
      /**/
      val vis = trainingExamplesAccuracySession.lineStreaming(dataArray, sizeArray, color = colorArray, xaxis = visName)
      /*this will be used for updating each visualzation*/
      problemTrainingExamplesLightningVisualizationsMap  +=  visName -> vis
    }}
  }

  /**/

  def initBroadcastData(sc: SparkContext) : Unit = {
    SMARTLogManager.log(getClass.getName, "Initializing & Sensing Broadcast Variables to Spark Workers")

    val environmentVariables = environmentSettings.environmentVariables.map(ev => (ev.name, ev)).toMap
    val environmentAttributes = environmentSettings.environmentAttributes.map(ea => (ea.name, ea)).toMap
    /**/
    var sensorIdToVariableNameMap = Map [String, String]()
    environmentInstances.foreach(i => sensorIdToVariableNameMap ++= i.sensorMappings)
    /**/
    var sensorIdToEnvironmentInstanceIdMap = Map [String, Int] ()
    environmentInstances.foreach(i => i.sensorMappings.keySet.foreach(sensorId => sensorIdToEnvironmentInstanceIdMap += sensorId -> i.id))
    /**/
    var environmentInstanceIdToAttributeValuesMap = Map [Int, Map[String, Double]]()
    environmentInstances.foreach(i => environmentInstanceIdToAttributeValuesMap+= i.id -> i.attributeValues)
    /**/
    var predictionProblemsSettings =  Map[String, PredictionProblemSettings]()
    predictionProblems.values.foreach(predictionProblem => predictionProblemsSettings += predictionProblem.name -> predictionProblem.problemSettings)
    /**/
    var goalVariableSensorIdToNonGoalVariablesSensorIdsMap = Map [String, Set[String]] ()
    predictionProblems.values.foreach{predictionProblem => {
      environmentInstances.foreach{ instance => {
        var instanceNonGoalVariablesSensorIdsSet = Set[String]()
        var instanceGoalVariableSensorId = ""
        /**/
        instance.sensorMappings.foreach(t => {
          val sensorId = t._1
          val variableName = t._2
          if (variableName == predictionProblem.goalVariable)
            instanceGoalVariableSensorId = sensorId
          else if (predictionProblem.explanatoryVariables.contains(variableName))
            instanceNonGoalVariablesSensorIdsSet += sensorId
        })
        if (!goalVariableSensorIdToNonGoalVariablesSensorIdsMap.contains(instanceGoalVariableSensorId))
          goalVariableSensorIdToNonGoalVariablesSensorIdsMap += (instanceGoalVariableSensorId -> instanceNonGoalVariablesSensorIdsSet)
        else {
          val oldSet = goalVariableSensorIdToNonGoalVariablesSensorIdsMap.get(instanceGoalVariableSensorId).get
          goalVariableSensorIdToNonGoalVariablesSensorIdsMap += (instanceGoalVariableSensorId -> oldSet.union(instanceNonGoalVariablesSensorIdsSet))
        }
      }}
    }}
    /**/
    var problemNameToExplanatoryVariablesMap = Map[String, Map[String, Int]] ()
    predictionProblems.values.foreach(predictionProblem => {
      var predictionProblemExplanatoryVariablesList = predictionProblem.explanatoryVariables
      problemNameToExplanatoryVariablesMap += predictionProblem.name -> predictionProblemExplanatoryVariablesList.zipWithIndex.toMap
    })
    /**/
    var problemNameToGoalVariableMap = Map[String, String] ()
    predictionProblems.values.foreach(predictionProblem => {
      problemNameToGoalVariableMap += predictionProblem.name -> predictionProblem.goalVariable
    })

    var instanceIdToLightningVisualizationsMap = initEnvironmentInstancesStateVisualizations

    settingsBroadcast = new SettingsBroadcast(sc, environmentVariables, environmentAttributes, sensorIdToVariableNameMap,
      sensorIdToEnvironmentInstanceIdMap, environmentInstanceIdToAttributeValuesMap, predictionProblemsSettings,
      goalVariableSensorIdToNonGoalVariablesSensorIdsMap, problemNameToExplanatoryVariablesMap, problemNameToGoalVariableMap,
      instanceIdToLightningVisualizationsMap)
  }

  private def initEnvironmentInstancesStateVisualizations : Map [Int, Map[String, Visualization]]  = {

    SMARTLogManager.log(getClass.getName, "Initializing Workers visualization objects")
    var instanceIdToLightningVisualizationsMap = Map[Int, Map[String, Visualization]]()

    SMARTRuntime.environmentInstances.foreach(environmentInstance => {
      val instanceId = environmentInstance.id
      /*creating a session for each environment instance to visualize the instance state variables separately*/
      val lightningSession = Lightning("http://" + SMARTRuntime.lightningVisualization.host + ":" + SMARTRuntime.lightningVisualization.port)
      lightningSession.createSession(SMARTRuntime.environmentSettings.environmentName +": "+ instanceId)
      var instanceVisualizationsMap = Map[String, Visualization]()

      SMARTRuntime.lightningVisualization.environmentStateVis.foreach{ case (visName, visEntities) => {
        val dataArray = new Array[Array[Double]] (visEntities.size)
        val sizeArray = new Array[Double] (visEntities.size)
        val colorArray = new Array[Array[Double]] (visEntities.size)
        for (i <-0 until visEntities.size ){
          dataArray(i) = Array(0.0)/*init value for streaming vis for each entity*/
          sizeArray(i) = 10 /*reasonable size in the display*/
          colorArray(i) = visEntities(i).color
        }
        /**/
        val vis = lightningSession.lineStreaming(dataArray, sizeArray, color = colorArray, xaxis = visName)
        /*this will be used for updating each visualzation*/
        instanceVisualizationsMap  +=  visName -> vis
      }}
      instanceIdToLightningVisualizationsMap += instanceId -> instanceVisualizationsMap
    })
    instanceIdToLightningVisualizationsMap
  }


  def main (args : Array[String]): Unit ={
    start(inputConfigPath = "/home/elsioufy/Desktop/in.json")
  }






}