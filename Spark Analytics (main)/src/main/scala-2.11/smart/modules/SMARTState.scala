package smart.app.modules

import org.apache.spark.streaming.dstream.DStream
import smart.app.{SMARTRuntime, SMARTLogManager}
import smart.util.io.util.CassandraUtil
import com.datastax.spark.connector.streaming._

object SMARTState  {

  def updateEnvironmentState(sensorReadingsStream: DStream[(String, Long, Double)]): DStream[(Int, Map[String, (Long, Double)])] = {
    sensorReadingsStream
      .map(t => (SMARTRuntime.settingsBroadcast.sensorIdToEnvironmentInstanceIdMapBC.value(t._1), (SMARTRuntime.settingsBroadcast.sensorIdToVariableNameMapBC.value(t._1), t._2, t._3)))/*(key: instanceId, value:(varName, ts, value))*/
      .updateStateByKey(updateEnvironmentsState)
  }

  private def updateEnvironmentsState(newValues: Seq[(String, Long, Double)], oldState: Option[Map[String, (Long, Double)]]): Option[Map[String, (Long, Double)]] = {
    /*The state of a target environment as the latest sensor recording for each of its environment variables*/

    /* sort by timestamp asc, so that new states will override the older ones, executed @ single worker*/
    val orderByTimeStamp = Ordering.by { foo: (String, Long, Double) => foo._2 }
    val sortedVals = newValues.sorted(orderByTimeStamp)
    var state = oldState.getOrElse(Map[String, (Long, Double)]())
    /*overriding older states*/
    sortedVals.foreach { case (varName, ts, value) => state += varName -> (ts,value)}
    Some(state)
  }

  def visualizeEnvironmentsState(environmentStateStream: DStream[(Int, Map[String, (Long, Double)])]): Unit = {
    environmentStateStream.foreachRDD( rdd => {
      /*discarding the timestamps : we will only visualize the values*/
      rdd.map{case (environmentInstance, stateTimeStampedValueMap) => (environmentInstance, stateTimeStampedValueMap.mapValues(t => t._2))  }

        .foreach{ case (environmentInstance, statesValuesMap) => {
        SMARTRuntime.lightningVisualization.environmentStateVis.foreach{case (visname, visEntities) => {
          val dataArray = new Array[Array[Double]](visEntities.size)
          var idx = 0
          val itr = visEntities.iterator
          while (itr.hasNext) {
            val evName = itr.next().name
            val evState = statesValuesMap.getOrElse(evName, 0.0)
            dataArray(idx) = Array(evState)
            idx += 1
          }
          SMARTRuntime.settingsBroadcast.instanceIdToLightningVisualizationsMapBC.value(environmentInstance)(visname).appendData(dataArray)
        }}
      }}
    })
  }

  def persistSensorDataStream(sensorReadingsStream: DStream[(String, Long, Double)]) : Unit = {
    if (SMARTRuntime.cassandraPersistence.create){
      SMARTLogManager.log(getClass.getName, "creating cassandra table for persistence of environments state data @  "+ SMARTRuntime.cassandraPersistence.keyspaceName+"."+SMARTRuntime.cassandraPersistence.tableName)
      /*we set the replication factor to the cassandra cluster size*/
      CassandraUtil.createCassandraTable(sensorReadingsStream.conf, SMARTRuntime.cassandraPersistence.keyspaceName,
        SMARTRuntime.cassandraPersistence.hostsToPortsMap.size,
        SMARTRuntime.cassandraPersistence.tableName,
        baseCassandraTableDefinition())
    }
    sensorReadingsStream.saveToCassandra(SMARTRuntime.cassandraPersistence.keyspaceName, SMARTRuntime.cassandraPersistence.tableName)
  }

  private def baseCassandraTableDefinition(reverseOrder: Boolean = false): String = {
    var res =
      "(\n" +
        "sensor_uuid text,\n"  +
        "unix_ts bigint,\n" +
        "value double,\n" +
        "PRIMARY KEY (sensor_uuid, unix_ts),\n" +
        ")"
    if (reverseOrder)
      res += "WITH CLUSTERING ORDER BY (unix_ts DESC);"
    else
      res += "WITH CLUSTERING ORDER BY (unix_ts ASC);"
    res
  }

}