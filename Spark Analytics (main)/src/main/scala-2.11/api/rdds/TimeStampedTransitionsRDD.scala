package api.rdds

import api.SamplingInterval
import org.apache.spark.rdd.RDD
import org.apache.spark.{TaskContext, Partition}


class TimeStampedTransitionsRDD (rdd: RDD[((Long, Double), (Long, Double))], samplingInterval: SamplingInterval) extends RDD[((Long, Double), (Long, Double))](rdd){

  override def compute(split: Partition, context: TaskContext): Iterator[((Long, Double),(Long, Double))] = firstParent[((Long, Double),(Long, Double))].iterator(split, context)
  override protected def getPartitions: Array[Partition] = firstParent[(Long, Double)].partitions

  /*assumes both rdds have the same sampling interval*/
  def findTransitionsAverage(sampledTimeStampedValueRDD: SampledTimeStampedValueRDD): RDD[(((Long, Double), (Long, Double)), Double)] = {

    rdd
      .flatMap { case (tsv1, tsv2) => {
      var res = List[(Long, ((Long, Double), (Long, Double)))]()
      val transitionSamplingInterval = new SamplingInterval(tsv1._1, tsv2._1, samplingInterval.getIncrement)
      var transitionSamplingPoints = transitionSamplingInterval.getSamplingPoints()
      /*dropRight: we don't consider the lastTimestamp. It will be included as the beginningTS in the next transition*/
      if (transitionSamplingPoints.takeRight(1).head == tsv2._1)
          transitionSamplingPoints = transitionSamplingPoints.dropRight(1)
        transitionSamplingPoints.foreach(l => res ::=(l, (tsv1, tsv2)))
      res
    }}
      .join(sampledTimeStampedValueRDD)
      .map { case (key, (transition, value)) => (transition, (value, 1)) }
      .reduceByKey((vc1, vc2) => (vc1._1+vc2._1, vc1._2+vc2._2))
      .map { case (transition, (value, count)) => (transition, value / count) }
  }

  def findTransitionsAverage(sampledTimeStampedValueRDDsMap: Map[String, SampledTimeStampedValueRDD]) : Map[String ,RDD[(((Long, Double), (Long,Double)), Double)]] = {
    sampledTimeStampedValueRDDsMap.mapValues(findTransitionsAverage(_))
  }

  /**/

  def getRdd = rdd
  def getSamplingInterval = samplingInterval
  def print = {
    samplingInterval.print
    println(rdd.collect.mkString(" "))
  }

}