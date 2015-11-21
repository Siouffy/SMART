package api

class SamplingInterval (fromTimestamp: Long, toTimestamp: Long, increment: Int) extends Serializable {

  def getSamplingPoints (): List[Long] ={
    var res = List[Long]()
    var i = fromTimestamp
    while (i <= toTimestamp) {
      res ::= i
      i+= increment
    }

    /*if (forceLastTimestamp) --> toTimestamp will be included as the last element whether or not it results
    * from incrementing the fromTimestamp*/
    /*
    i-= increment
    val forceLastTimestamp: Boolean = false
    if (i != toTimestamp && forceLastTimestamp)
      res ::= toTimestamp
    */
    res
  }
  def upsample : SamplingInterval = new SamplingInterval(fromTimestamp, toTimestamp, increment/2)
  def downsample : SamplingInterval = new SamplingInterval(fromTimestamp, toTimestamp, increment*2)
  /**/
  def print : Unit = println("SamplingInterval("+fromTimestamp+", "+toTimestamp+", "+increment+")")
  def getFromTimestamp : Long = fromTimestamp
  def getToTimestamp : Long = toTimestamp
  def getIncrement : Int = increment
}