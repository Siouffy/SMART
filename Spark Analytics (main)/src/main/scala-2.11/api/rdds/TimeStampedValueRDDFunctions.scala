package api.rdds

import api.SamplingInterval
import org.apache.spark.rdd.RDD


class TimeStampedValueRDDFunctions(rdd:RDD[(Long, Double)]) {


  def toTimeStampedValueRDD(samplingFunction: (Long, SamplingInterval) => List[Long], samplingInterval: SamplingInterval): TimeStampedValueRDD ={
      (new TimeStampedValueRDD(rdd, samplingInterval)).resample(samplingFunction, samplingInterval)
  }
}

object TimeStampedValueRDDFunctions {
  implicit def addTimeStampedValueRDDFunctions(rdd: RDD[(Long, Double)]) = (new TimeStampedValueRDDFunctions(rdd))
}