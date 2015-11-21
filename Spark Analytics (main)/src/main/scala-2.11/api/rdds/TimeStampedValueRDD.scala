package api.rdds

import api.SamplingInterval
import org.apache.spark.storage.StorageLevel
import org.apache.spark.{TaskContext, Partition}
import org.apache.spark.rdd.RDD

class TimeStampedValueRDD (rdd:RDD[(Long, Double)], samplingInterval:SamplingInterval) extends RDD[(Long, Double)](rdd) {

  override def compute(split: Partition, context: TaskContext): Iterator[(Long, Double)] = firstParent[(Long, Double)].iterator(split, context)
  override protected def getPartitions: Array[Partition] = firstParent[(Long, Double)].partitions

  /*the resampling function could be any function that takes a timestamp and smapling interval and resamples the
  * timestamp on that sampling interval .. examples of the reampling funcion are [sample][upsmaple][downsample]*/

  def resample (resamplingFunction: (Long, SamplingInterval) => List[Long], newSamplingInterval: SamplingInterval) : TimeStampedValueRDD = {
    val resampledRdd = rdd.flatMap(tsv => resamplingFunction(tsv._1, samplingInterval).map(samplingPoint => (samplingPoint, tsv._2)))
      .map(t => (t._1, (t._2, 1)))
      .reduceByKey((vc1, vc2) => (vc1._1+vc2._1, vc1._2+vc2._2))
      .map(t => (t._1, t._2._1 / t._2._2))
      new TimeStampedValueRDD(resampledRdd, newSamplingInterval)
  }

  def isSampled : Boolean = {
    val nSamplingPoints = ((samplingInterval.getToTimestamp-samplingInterval.getFromTimestamp)/samplingInterval.getIncrement).toInt +1
    /*count shall always be less than or equals nSamplingPoints*/
    count() == nSamplingPoints
  }

  def toSampledTimeStampedValueRDD (downsamplingFunction: (Long, SamplingInterval) => List[Long], upsamplingFunction: (Long, SamplingInterval) => List[Long]): SampledTimeStampedValueRDD ={

    var x = List[TimeStampedValueRDD]()
    x ::= this

    /*keep down-sampling the data until all the sampling points are filled*/
    while (!x.head.isSampled) {
      /*data needs to be persisted because it will be used later and we don't want to redo the calculations [we have reduceByKey]*/
      x ::= x.head.resample(downsamplingFunction, x.head.getSamplingInterval.downsample).persist(StorageLevel.MEMORY_AND_DISK)
    }
    /*now x.head confirms to characteristics of a SampledTimeStampedValueRDD*/
    /*up-sampling the data till reaching the original sampling rate, while using the original values whenever possible*/
    var y = x.head
    while (x.size > 1){
      x.head.unpersist()
      x = x.drop(1)
      y = y.resample(upsamplingFunction, y.getSamplingInterval.upsample)
      /*combining the data together
      * temp: higherSampledData
      * x.head: original
      * if x.head contains data at a given samplePoint, then temp data at that sample point is ignored
      * if x.head is missing data at a given samplePoint, then temp data at that sample point is considered*/
      val tempRDD = x.head.rightOuterJoin(y)
        .map(t=> {
        if (t._2._1.equals(None)) (t._1, t._2._2)
        else (t._1, t._2._1.get)
      })
      y = new TimeStampedValueRDD(tempRDD, y.getSamplingInterval)
      /*to enforce any action: bec. of lazy evaluation*/
      y.count()
    }
    x.head.unpersist()
    x = x.drop(1)
    y.toSampledTimeStampedValueRDD
  }

  def mapValues (valueMappingFunction: Double => Double): TimeStampedValueRDD ={
    val resRDD = rdd.map(t => (t._1, valueMappingFunction(t._2)))
    new TimeStampedValueRDD(resRDD, samplingInterval)
  }

  /**/

  def getRdd = rdd
  def getSamplingInterval = samplingInterval
  def print = {
    samplingInterval.print
    println(rdd.collect.mkString)
  }

  /**/

  private def toSampledTimeStampedValueRDD = new SampledTimeStampedValueRDD (rdd, samplingInterval)




}



