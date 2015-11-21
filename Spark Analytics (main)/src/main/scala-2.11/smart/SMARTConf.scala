package smart

import smart.app.SMARTStructures._
import net.liftweb.json._
import net.liftweb.json.Serialization


class SMARTConf {

  /*user input: default values*/
  var appName : String = "test app"
  var masterURL : String = "local[*]"
  var debug = true

  var latency : Latency =  new Latency(2, 60)
  var environmentSettings: EnvironmentSettings = new EnvironmentSettings("na", Set[EnvironmentVariable](), Set[EnvironmentAttribute](), 2)
  var environmentInstances = Set[EnvironmenInstance]()
  var predictionProblems = Map[String, PredictionProblem]()
  var cassandraPersistence : CassandraPersistence = CassandraPersistence(Map[String, Int](), "na", "na", false)
  var lightningVisualization : LightningVisualization = LightningVisualization("localhost", 3000, Map[String, List[VisualizationEntity]](), Map[String, List[VisualizationEntity]](), Map[String, List[VisualizationEntity]]())
  var ioSettings : IOSettings = IOSettings(Set[RMQQueue](), Set[RMQExchange](), Set[KafkaTopics]())
  /****/
  var applicationSettings : ApplicationSettings = null

  def setAppName (n: String) = appName = n
  def setMasterURL (u: String) = masterURL = u
  def setDebug (d: Boolean) = debug = d
  def setLatency(l: Latency) = latency = l
  /**/
  def setEnvironmentName (name: String) = environmentSettings = environmentSettings.copy(environmentName = name)
  def setEnvironmentSampling (s: Int) = environmentSettings = environmentSettings.copy(sampling = s)
  def addEnvironmentVariable (ev: EnvironmentVariable) = environmentSettings = environmentSettings.copy(environmentVariables = environmentSettings.environmentVariables + ev)
  def addEnvironmentAttribute (ea: EnvironmentAttribute) = environmentSettings = environmentSettings.copy(environmentAttributes = environmentSettings.environmentAttributes + ea)
  def addEnvironmentInstance (inst: EnvironmenInstance) = environmentInstances = environmentInstances + inst
  /**/
  def addPredictionProblem (p:PredictionProblem) = predictionProblems = predictionProblems + (p.name -> p)
  def setCassandraPersistence(cp: CassandraPersistence) = cassandraPersistence = cp
  /**/
  def setLightningVisualization(lv: LightningVisualization) = lightningVisualization = lv
  def setVisHost(h: String) = lightningVisualization = lightningVisualization.copy(host = h)
  def setVisPort(p: Int) = lightningVisualization = lightningVisualization.copy(port = p)

  def addLastBatchAccuracyVisualization(name: String, visEntities: List[VisualizationEntity]) = lightningVisualization = lightningVisualization.copy(lastBatchAccuracyVis = lightningVisualization.lastBatchAccuracyVis + (name->visEntities))
  def addLastBatchNTrainingExamplesVisualization(name: String, visEntities: List[VisualizationEntity]) = lightningVisualization = lightningVisualization.copy(lastBatchNTrainingExamplesVis = lightningVisualization.lastBatchNTrainingExamplesVis + (name->visEntities))
  def addEnvironmentStateVisualization(name: String, visEntities: List[VisualizationEntity]) = lightningVisualization = lightningVisualization.copy(environmentStateVis = lightningVisualization.environmentStateVis + (name->visEntities))
  /**/
  def addRMQQueue(s: RMQQueue) = ioSettings = ioSettings.copy(rmqQueues = ioSettings.rmqQueues + s)
  def addRMQExchange(s: RMQExchange) = ioSettings = ioSettings.copy(rmqExchanges = ioSettings.rmqExchanges + s)
  def addKafkaTopics(s: KafkaTopics) = ioSettings = ioSettings.copy(kafkaTopics = ioSettings.kafkaTopics + s)

  
  def createApplicationSettings : ApplicationSettings = {
    applicationSettings =  ApplicationSettings (appName, masterURL, debug, latency, environmentSettings, environmentInstances,
      predictionProblems, cassandraPersistence, lightningVisualization, ioSettings)
    applicationSettings
  }

  def createApplicationSettingsAsJSON : String = {
    applicationSettings =  ApplicationSettings (appName, masterURL, debug, latency, environmentSettings, environmentInstances,
      predictionProblems, cassandraPersistence, lightningVisualization, ioSettings)
    implicit val formats = Serialization.formats(NoTypeHints)
    Serialization.write(applicationSettings)
  }
  
}