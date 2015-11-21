package smart.app.modules

import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.dstream.DStream

import scala.reflect.ClassTag


object SMARTUtil {

  def union [T: ClassTag] (rdds : Seq[RDD[T]]) : RDD[T] = {
    var res = rdds.head
    val iterator = rdds.iterator
    iterator.next()
    /**/
    while (iterator.hasNext)
      res = res.union(iterator.next())
    /**/
    res
  }

  def union [T: ClassTag] (dss : Seq[DStream[T]]) : DStream[T] = {

    var res = dss.head
    val iterator = dss.iterator
    iterator.next()
    /**/
    while (iterator.hasNext)
      res = res.union(iterator.next())
    /**/
    res
  }

  def toRDDsMapByKey[U: ClassTag](rdd: RDD[(String, U)], keySet: Set[String]): Map[String, RDD[U]] = {
    var res = Map[String, RDD[U]]()
    keySet.foreach(key => res += (key -> rdd.filter(t => t._1 == key).map(t => t._2)))
    res
  }

  def toDStreamsMapByKey[U: ClassTag](ds: DStream[(String, U)], keySet: Set[String]): Map[String, DStream[U]] = {
    var res = Map[String, DStream[U]]()
    keySet.foreach(key => res += (key -> ds.filter(t => t._1 == key).map(t => t._2)))
    res
  }

}