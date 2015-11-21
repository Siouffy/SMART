package smart

import smart.app.SMART._
import smart.app.SMARTStructures._

object SMARTLauncher {

  def main(args: Array[String]) {
    if (args == null)
      start()
    else if (args.size == 0)
      start()
    else
      start(inputConfigPath = args(0))
  }

  def main_(args: Array[String]) {
    val smartConf = new SMARTConf()

    smartConf.setAppName("SMART Room Analyzer")
    smartConf.setMasterURL("spark://10.0.0.51:7077")
    smartConf.setDebug(true)
    smartConf.setLatency(Latency(1,60))
    /**//**/
    smartConf.setEnvironmentName("room")
    smartConf.addEnvironmentVariable(EnvironmentVariable("room_occupants", 0,30))
    smartConf.addEnvironmentVariable(EnvironmentVariable("ext_temp", 0,30))
    smartConf.addEnvironmentVariable(EnvironmentVariable("heating_level", 0,5))
    smartConf.addEnvironmentVariable(EnvironmentVariable("room_temp", 0,30))

    smartConf.addEnvironmentAttribute(EnvironmentAttribute("room_material", 0, 10))
    smartConf.addEnvironmentAttribute(EnvironmentAttribute("room_volume", 0, 100))
    smartConf.addEnvironmentAttribute(EnvironmentAttribute("heating_capacity", 0, 10))
    smartConf.setEnvironmentSampling(2)
    /**//**/
    smartConf.addEnvironmentInstance(EnvironmenInstance(300, Map( "S12" -> "room_occupants", "S23" -> "ext_temp", "S34" -> "heating_level", "T45" -> "room_temp"),
      Map("room_material" -> 5 ,"room_volume" -> 50,"heating_capacity" -> 5)))
    /**//**/
    smartConf.addPredictionProblem(PredictionProblem("room_heating_time_prediction_n_p", "room_temp",
      List("room_occupants", "ext_temp", "heating_level", "room_material", "room_volume", "heating_capacity"),
      PredictionProblemSettings("polinomail", true, List[Double](), 0),
      CrossValidationSettings(3,5) ))

    smartConf.addPredictionProblem(PredictionProblem("room_heating_time_prediction_n_l", "room_temp",
      List("room_occupants", "ext_temp", "heating_level", "room_material", "room_volume", "heating_capacity"),
      PredictionProblemSettings("linear", true, List[Double](), 0),
      CrossValidationSettings(3,5) ))
    /**//**/
    smartConf.setCassandraPersistence(CassandraPersistence(Map("10.0.0.31" -> 9042, "10.0.0.32" -> 9042), "test_keyspace", "test_data", true))
    /**//**/
    smartConf.setVisHost("10.0.0.41")
    smartConf.setVisPort(3000)
    val visEntitiesLst1 = List(
      VisualizationEntity("room_heating_time_prediction_n_p", Array(0,255,0)),
      VisualizationEntity("room_heating_time_prediction_n_l", Array(0,0,255)))

    val visEntitiesLst2 = List(
      VisualizationEntity("room_temp", Array(0,0,255)),
      VisualizationEntity("ext_temp", Array(0,255,0)),
      VisualizationEntity("room_occupants", Array(255,0,0)),
      VisualizationEntity("heating_level", Array(120,120,120))
    )

    smartConf.addLastBatchAccuracyVisualization("lastBatchAccuracy_all", visEntitiesLst1)
    smartConf.addLastBatchAccuracyVisualization("lastBatchAccuracy_polinomial", List(VisualizationEntity("room_heating_time_prediction_n_p", Array(0,255,0))))
    smartConf.addLastBatchAccuracyVisualization("lastBatchAccuracy_linear", List(VisualizationEntity("room_heating_time_prediction_n_l", Array(0,0,255))))

    smartConf.addLastBatchNTrainingExamplesVisualization("lastBatchNTrainingExamples_any", List(VisualizationEntity("room_heating_time_prediction_n_l", Array(0,0,255))))

    smartConf.addEnvironmentStateVisualization("StateVis", visEntitiesLst2)
    smartConf.addEnvironmentStateVisualization("TempVis", List(VisualizationEntity("room_temp", Array(0,0,255))))


    smartConf.addRMQQueue(RMQQueue(Map("10.0.0.21" -> 5672), "request_queue", "queryRequest"))
    smartConf.addRMQQueue(RMQQueue(Map("10.0.0.21" -> 5672), "response_queue", "queryResponse"))
    smartConf.addRMQExchange(RMQExchange( Map("10.0.0.21" -> 5672), "test_direct", "direct", Set("key1", "key2"), "__"))
    smartConf.addRMQExchange(RMQExchange( Map("10.0.0.21" -> 5672), "state_exchange", "topic", Set("a.b.*"), "stateIn"))
    smartConf.addKafkaTopics(KafkaTopics(Map("10.0.0.11" -> 2181), Map("10.0.0.12" -> 9092, "10.0.0.13" -> 9092), Set("topic1","topic2"), "__" ))


    start(applicationSettings= smartConf.createApplicationSettings)
  }

}
