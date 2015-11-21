package smart.app

import java.io.File

import _root_.api.SamplingInterval
import com.datastax.spark.connector.cql.CassandraConnector
import org.apache.commons.io.FileUtils
import org.viz.lightning.{Visualization, Lightning}
import smart.app.SMART._
import smart.app.SMARTRuntime._
import smart.app.SMARTRuntime.start
import smart.app.modules._
import smart.util.io.util.CassandraUtil


/*spark-core*/
import org.apache.spark.{SparkContext, SparkConf}

/*spark streaming*/
import org.apache.spark.streaming._
import org.apache.spark.streaming.dstream.DStream
/*spark mllib*/
import org.apache.spark.mllib.regression.LabeledPoint

/*others*/
import smart.app.SMARTStructures._


object SMART {
  var checkpointDir : String  = null /*used to maintain objects used in stateful operations*/
  /*input streams*/
  var sensorDataStream: DStream[(String, Long, Double)] = null /*streaming latency*/
  var queryRequestsStream: DStream[(Int, (Int, String, Map[String, Double], Int))] = null /*streaming latency*/
  /*generated streams*/
  var environmentsStateStream: DStream[(Int, Map[String, (Long, Double)])] = null /*streaming latency*/
  var featureVectorsStream: DStream[(String, LabeledPoint)] = null /*learning latency*/
  var problemsStateStream: DStream[(String, (Double, Long))] = null /*learning latency*/
  var queryResponsesStream: DStream[String] = null /*streaming latency*/

  def start(inputConfigPath: String = null,  applicationSettings: ApplicationSettings = null): Unit = {
    SMARTLogManager.setLogLevels() /*disabling some sparks logs*/
    SMARTRuntime.start(inputConfigPath, applicationSettings)

    if (SMARTRuntime.masterURL.startsWith("local")){
      checkpointDir = "smart_checkpoint"
      FileUtils.deleteDirectory(new File(checkpointDir))
    }
    else { checkpointDir = "hdfs://smart_checkpoint" }

    val ssc = StreamingContext.getOrCreate(checkpointDir, createStreamingContext)
    start(ssc)
  }

  private def createStreamingContext(): StreamingContext = {
    SMARTLogManager.log(getClass.getName, "Initializing Streaming Context [no checkpoint directory was found]")
    /*INITIALIZING THE APPLICATION, CONFIGURATION AND THE STREAMING MODULE */
    val conf = initSparkConfiguration()
    val sc = new SparkContext(conf)
    val ssc = new StreamingContext(sc, Seconds(SMARTRuntime.latency.streaming))
    /* Check-pointing must be enabled for applications that use stateful transformations.
     * We use stateful transformations [updateStateByKey] in order to preserve the state for each target environment instance*/
    ssc.checkpoint(checkpointDir)
    ssc
  }

  private def initSparkConfiguration(): SparkConf = {

    new SparkConf()
      /* The appName parameter is a name for the application to show on the cluster UI.*/
      .setAppName(SMARTRuntime.appName)

      /* master is a Spark, Mesos or YARN cluster URL, or a special “local” string to run in local mode.*/
      .setMaster(SMARTRuntime.masterURL)

      /* By default, Spark serializes objects using Java’s ObjectOutputStream framework, and can work with any class
       * that implements java.io.Serializable. Java serialization is flexible but often quite slow and leads to large
       * serialized formats for many classes.
       *
       * Spark can also use the Kryo library to serialize objects more quickly.
       * Kryo is significantly faster and more compact than Java serialization (often as much as 10x), but does not
       * support all Serializable types and requires you to register the classes you’ll use in the program in advance
       * for best performance. If you don’t register your custom classes, Kryo will still work, but it will have to
       * store the full class name with each object, which is wasteful.*/
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .registerKryoClasses(
        Array(
          classOf[SamplingInterval],
          classOf[PredictionProblemLearningData],
          classOf[Lightning],
          classOf[Visualization]))
      /* In order to connect to and write data to cassandra we use the datastax spark-cassandra-connector
       * using the connector, we need to set the spark.cassandra.connection.host configuration
       * property before creating the SparkContext. Regardless the size of the cassandra cluster, we are allowed to specify
       * the host address of a single node within the cluster (being seed or not doesn't matter). From that node the
       * connector grabs the cassandra cluster information (i.e. other hosts addresses ..etc.)
       * [DEFAULT VALUE]: address of the Spark master host
       * -------------------------------------------------------------------------------------------------------------
       * https://github.com/datastax/spark-cassandra-connector/blob/master/doc/1_connecting.md
       * List of other settings to include for connection with cassandra, ex. including username, and password .. etc
       * -------------------------------------------------------------------------------------------------------------*/
      //.set("spark.cassandra.auth.username", "username_if_any")
      //.set("spark.cassandra.auth.password", "password_if_any")
      .set("spark.cassandra.connection.host", SMARTRuntime.cassandraPersistence.hostsToPortsMap.keys.toList.head)

      /*for slow tasks ... */
      //.set("spark.speculation", "true")
      //.set("spark.speculation.interval", "1000ms")
  }

  private def start(ssc: StreamingContext): Unit = {
    SMARTRuntime.initBroadcastData(ssc.sparkContext)
    fT(ssc.sparkContext.getConf) /*thread for persisting the problems state data*/
    /** ****************************************************************************************************************/
    sensorDataStream = SMARTIO.streamIn(ssc, "stateIn").map(SMARTFunctions.sensorDataParsingFunction) //sensorDataStream = TCPStreamReceiver.streamInTestState(ssc, 9999)
    queryRequestsStream = SMARTIO.streamIn(ssc, "queryRequest").map(SMARTFunctions.queryRequestParsingFunction) //queryRequestsStream = TCPStreamReceiver.streamInTestQueryRequests(ssc, 1111)
    /******/

    SMARTState.persistSensorDataStream(sensorDataStream)
    environmentsStateStream = SMARTState.updateEnvironmentState(sensorDataStream)
    SMARTState.visualizeEnvironmentsState(environmentsStateStream)
    /******/

    featureVectorsStream = SMARTLearning.createFeatureVectorsStream(sensorDataStream)
    problemsStateStream = SMARTLearning.trainAndEstimateProblemsState(featureVectorsStream)
    SMARTLearning.visualizeProblemsState(problemsStateStream)
    /******/

    queryResponsesStream = SMARTAnalytics.processUserQueries(queryRequestsStream, environmentsStateStream)
    SMARTIO.streamOutQueryResponses(ssc, queryResponsesStream)

    ssc.start()
    ssc.awaitTermination()
  }
  /** ******************************************************************************************************************/
  /** ******************************************************************************************************************/

  private def fT (conf: SparkConf) : Unit = {

    new Thread(new Runnable {
      def run() {
        val sleepLatency = SMARTRuntime.latency.learning *1000
        createFTCassandraTable(conf)
        while (true) {
          Thread.sleep(sleepLatency) /*storing approx. every batch*/
          SMARTRuntime.problemNameToProblemLearningDataMap.foreach{case (problemName, problemLearningData) => {
            val nTrainingExamples = problemLearningData.nTrainingExamples
            val state = problemLearningData.learningModel.latestModel().weights.toArray.mkString(" ")+"#"+
              problemLearningData.learningModel.latestModel().intercept+"#"+problemLearningData.getCurrentBatchError
            storeProblemStateData(conf, problemName, nTrainingExamples, state, sleepLatency*10)
          }}
        }
      }
    }).start()

  }
  
  def createFTCassandraTable(conf: SparkConf): Unit = {
    SMARTLogManager.log(getClass.getName, "Creating cassandra table for persistence of application state [Fault Tolerance]")
    var res =
      "(\n" +
        "problem_name text,\n"  +
        "n_training_examples bigint,\n" +
        "state text,\n" +
        "PRIMARY KEY (problem_name, n_training_examples),\n" +
        ")WITH CLUSTERING ORDER BY (n_training_examples DESC);"

    CassandraUtil.createCassandraTable(conf, "smart_metadata",
      SMARTRuntime.cassandraPersistence.hostsToPortsMap.size,
      "problem_state_data", res)

    SMARTLogManager.log(getClass.getName, "Application (Prediciton Problems) State will be periodically stored in Cassandra @ smart_metadata.problem_state_data")
  }

  def storeProblemStateData(conf: SparkConf, problemName: String, nTrainingExamples: Int, state: String, ttl: Long): Unit = {
    SMARTLogManager.log(getClass.getName, "Storing State Data for problem "+ problemName +" to Cassandra")
    val q1 = "INSERT INTO smart_metadata.problem_state_data(problem_name, n_training_examples, state) VALUES ('"+problemName+"', "+nTrainingExamples+", '"+state+"');" /*USING TTL "+ttl+";"*/
    CassandraConnector(conf).withSessionDo {session => session.execute(q1)}
  }








//SELECT * FROM smart_metadata.problem_state_data WHERE problem_name='room_heating_time_prediction_n_l' Limit 1 ;
//SELECT * FROM smart_metadata.problem_state_data WHERE problem_name='room_heating_time_prediction_n_p' Limit 1 ;












}