
package smart.util.io.util

import com.datastax.spark.connector.cql.CassandraConnector
import org.apache.spark.SparkConf

/* A Cassandra cluster can have one or more keyspaces, which are analogous to databases.
 * Replication is configured at the keyspace level*/

/*CassandraConnector instances are serializable and therefore can be safely used in lambdas passed to
Spark transformations*/

object CassandraUtil {


  /*creates a new cassandra key space, that has the defined replication factor.
  * within that created key space, a cassandra table is created with the input table name and definition
  * if the key space already exists, the old key space is deleted*/

  def createCassandraTable (conf: SparkConf, keyspace: String, replicationFactor: Int, tableName :String, tableDefinition: String): Unit ={
    /*IF NOT EXISTS??*/
    val q1 = "DROP KEYSPACE IF EXISTS " + keyspace + ";"
    val q2 = "CREATE KEYSPACE " + keyspace + " WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor': " + replicationFactor + " };"
    val q3 = "CREATE TABLE " + keyspace + "."+ tableName + tableDefinition +";"

    CassandraConnector(conf).withSessionDo { session =>
      session.execute(q1)
      session.execute(q2)
      session.execute(q3)
    }
  }

}