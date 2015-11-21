package smart.app

import org.apache.log4j.{Level, Logger}
import org.apache.spark.Logging


object SMARTLogManager extends Logging {



  /*only logs if force is true or the application configuration is set to debug mode*/
  def log (className: String , text : String, force: Boolean = false): Unit = {
    if (force)
      println("["+className+"] "+text)
    else if (SMARTRuntime.debug)
      println("["+className+"] "+text)
  }



  def setLogLevels() {
    val log4jInitialized = Logger.getRootLogger.getAllAppenders.hasMoreElements
    if (!log4jInitialized) {
      // We first log something to initialize Spark's default logging, then we override the logging level.
      logInfo("Setting log level to [ERROR] for org, akka, spark and streaming")
      Logger.getRootLogger.setLevel(Level.ERROR)
      Logger.getLogger("org").setLevel(Level.ERROR)
      Logger.getLogger("akka").setLevel(Level.ERROR)
      Logger.getLogger("streaming").setLevel(Level.ERROR)
      Logger.getLogger("spark").setLevel(Level.ERROR)
    }
  }
}