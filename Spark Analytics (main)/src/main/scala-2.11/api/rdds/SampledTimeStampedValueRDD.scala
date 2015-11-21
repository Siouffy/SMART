package api.rdds

import api.SamplingInterval
import org.apache.spark.rdd.RDD


class SampledTimeStampedValueRDD (rdd: RDD[(Long, Double)], samplingInterval: SamplingInterval) extends TimeStampedValueRDD(rdd, samplingInterval) {

  def sampleByValue : TimeStampedValueRDD = {
    val resRDD = rdd
      .flatMap( tsv => {((tsv._1, tsv._2), true) :: ((tsv._1+ samplingInterval.getIncrement, tsv._2), false) :: Nil})
      .groupByKey
      .filter(t => t._2.size == 1 && t._2.head)
      .map{case ((timestamp, value), itrB) => (timestamp, value)}
      new TimeStampedValueRDD(resRDD, samplingInterval)
  }

  def sampleByValue_join : TimeStampedValueRDD = {
    /*true,false will mainly be used to join on key (TSValue) ...*/
    val left = rdd.map(tsv => (tsv, true)).cache()
    val right = rdd.map(tsv => ((tsv._1+samplingInterval.getIncrement, tsv._2), false))
    val resRDD = left.leftOuterJoin(right)
      .filter{case (tsv, (b, opB)) => opB.equals(None)}
      .map{case (tsv, (b, opB)) => tsv}
    new TimeStampedValueRDD(resRDD, samplingInterval)
  }

  def sampleByValue_ : TimeStampedValueRDD = {

    val resRDD = rdd
      .map(_.swap)
      .groupByKey
      .map{case (value, tsIterable)   => (value, tsIterable.toList.sorted)}
      .flatMap{case (value, sortedTsList) => {
      /*(TS, (TimeUnits, Value)) ==> from TS until TS+TimeUnits the value of the sensor was Value*/
      var res = List[(Long, (Int, Double) )] ()
      val itr = sortedTsList.iterator
      var initValue = itr.next()
      var initInc = samplingInterval.getIncrement
      while (itr.hasNext){
        val next = itr.next()
        if (next - initValue == initInc)
          initInc += samplingInterval.getIncrement
        else {
          res ::=(initValue, (initInc, value))
          initValue = next
          initInc = samplingInterval.getIncrement
        }
      }
      res ::=(initValue, (initInc, value))
      res
    }}
      .map{case (ts, (inc, value)) => (ts, value) }

    new TimeStampedValueRDD(resRDD, samplingInterval)
  }

  /**/

  def findAllTransitions :TimeStampedTransitionsRDD=  {
    val temp  = sampleByValue.cache()
    val temp2 = temp.cartesian(temp).filter{case (tsvalueFrom, tsValueTo) => tsvalueFrom._1 < tsValueTo._1}
    new TimeStampedTransitionsRDD(temp2, samplingInterval)
  }

  def findSuccessiveTransitions : TimeStampedTransitionsRDD = {
    val temp1 = rdd
      .flatMap( tsv => {((tsv._1, tsv._2), samplingInterval.getIncrement) :: ((tsv._1+samplingInterval.getIncrement, tsv._2), -samplingInterval.getIncrement) :: Nil})
      .groupByKey
      .filter(t => t._2.size == 1)
      .cache()

    val temp2 = temp1
      .filter(t => t._2.head>0)
      .map(t =>t._1)

    val temp3 = temp1.map(t => t._1)
      .map(_.swap)
      .groupByKey
      .flatMap{case (value, tsIter) => {
      var res = List[(Long, (Long, Double))] ()
      val iterator = tsIter.toList.sorted.iterator
      while (iterator.hasNext) {
        val fromTs = iterator.next()
        if (iterator.hasNext) {
          val toTs = iterator.next()
          res ::= (toTs, (fromTs, value))
        }
        else
          res ::= (0, (fromTs, value))
      }
      res
    }
    }
      .join(temp2)
      .map{ case (tsTo,((tsFrom,valFrom), valTo)) => ((tsFrom,valFrom),(tsTo, valTo))}
    new TimeStampedTransitionsRDD(temp3, samplingInterval)
  }

  def findSuccessiveTransitions_(keepLastTransition: Boolean) : TimeStampedTransitionsRDD = {
    val res = rdd.map(_.swap)
      .groupByKey
      .map{case (value, tsIterable)   => (value, tsIterable.toList.sorted)}
      .flatMap{case (value, sortedTsList) => {
      /*(TS, (TimeUnits, Value)) ==> from TS until TS+TimeUnits the value of the sensor was Value*/
      var res = List[(Long, (Int, Double) )] ()
      val itr = sortedTsList.iterator
      var initValue = itr.next()
      var initInc = samplingInterval.getIncrement

      while (itr.hasNext){
        val next = itr.next()
        if (next - initValue == initInc)
          initInc += samplingInterval.getIncrement
        else {
          res ::=(initValue, (initInc, value))
          initValue = next
          initInc = samplingInterval.getIncrement
        }
      }
      res ::=(initValue, (initInc, value))
      res
    }}
      .flatMap{case (ts, (tunits, value)) => { (ts+tunits, (-tunits, value)) :: (ts, (tunits, value)) :: Nil }}
      .groupByKey
      /*one element with +ve increment ... i don't need it !*/
      .filter{case (ts, tuValueItr)    => {
      if (!keepLastTransition && tuValueItr.size == 1)
        false
      else if ((tuValueItr.size == 1 && tuValueItr.head._1 > 0) )
        false
      else
        true
    }}
      .map(t=>{
      var fromTs = 0.toLong
      var fromVal = 0.toDouble
      var toTs = 0.toLong
      var toVal = 0.toDouble
      /***/
      if (t._2.size == 1) {
        fromTs = t._1 + t._2.head._1
        fromVal = t._2.head._2
      }
      else {
        toTs = t._1
        if (t._2.head._1 > 0){
          fromTs = t._1 + t._2.tail.head._1
          fromVal = t._2.tail.head._2
          toVal = t._2.head._2
        }
        else {
          fromTs = t._1 + t._2.head._1
          fromVal = t._2.head._2
          toVal = t._2.tail.head._2
        }
      }
      ((fromTs, fromVal),(toTs, toVal))
    })

    new TimeStampedTransitionsRDD(res, samplingInterval)
  }

}