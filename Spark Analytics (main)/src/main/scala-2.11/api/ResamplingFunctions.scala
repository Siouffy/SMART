package api

object ResamplingFunctions  {

  def sampleLeft (timestamp: Long, samplingInterval: SamplingInterval): List[Long] = {
    val t = ((timestamp.toDouble - samplingInterval.getFromTimestamp.toDouble)/samplingInterval.getIncrement.toDouble)
    val diff = t - t.toInt

    /*the point already lies within the sampling interval*/
    if (diff == 0)
      timestamp:: List()
    else
      samplingInterval.getFromTimestamp + t.toInt * samplingInterval.getIncrement :: List()
  }

  def sampleRight (timestamp: Long, samplingInterval: SamplingInterval): List[Long] = {
    val t = ((timestamp.toDouble - samplingInterval.getFromTimestamp.toDouble)/samplingInterval.getIncrement.toDouble)
    val diff = t - t.toInt

    /*the point already lies within the sampling interval*/
    if (diff == 0)
      timestamp:: List()
    else{
      var newTS = samplingInterval.getFromTimestamp + (t+1).toInt * samplingInterval.getIncrement
      /*if it goes outside the range, we have to map it to the smaller*/
      if (newTS > samplingInterval.getToTimestamp)
        newTS = samplingInterval.getFromTimestamp + t.toInt * samplingInterval.getIncrement
      newTS:: List()
    }
  }

  def sampleAccurate (timestamp: Long, samplingInterval: SamplingInterval) : List[Long] = {

    val t = ((timestamp.toDouble - samplingInterval.getFromTimestamp.toDouble)/samplingInterval.getIncrement.toDouble)
    val diff = t - t.toInt

    /*the point already lies within the sampling interval*/
    if (diff == 0)
      timestamp:: List()

    else if (diff < 0.5)
      samplingInterval.getFromTimestamp + t.toInt * samplingInterval.getIncrement :: List()

    else if (diff > 0.5){
      var newTS = samplingInterval.getFromTimestamp + (t+1).toInt * samplingInterval.getIncrement
      /*if it goes outside the range, we have to map it to the smaller*/
      if (newTS > samplingInterval.getToTimestamp)
        newTS = samplingInterval.getFromTimestamp + t.toInt * samplingInterval.getIncrement
      newTS:: List()
    }

    else /*diff == 0.5*/{
      val tsBefore = samplingInterval.getFromTimestamp + t.toInt * samplingInterval.getIncrement
      val tsAfter = samplingInterval.getFromTimestamp + (t+1).toInt * samplingInterval.getIncrement
      if (tsAfter > samplingInterval.getToTimestamp)
        tsBefore :: List()
      else
        tsBefore :: tsAfter :: List()
    }


  }

  /********************************************************************************************************************/
  /********************************************************************************************************************/

  def upsampleLeft (timestamp: Long, currentSamplingInterval: SamplingInterval): List[Long] = {
    var res = List[Long]()
    res ::= timestamp

    val newBeforeTs = timestamp - (currentSamplingInterval.getIncrement/2)
    if (newBeforeTs >= currentSamplingInterval.getFromTimestamp)
      res ::= newBeforeTs

    res
  }

  def upsampleRight (timestamp: Long, currentSamplingInterval: SamplingInterval): List[Long] = {
    var res = List[Long]()
    res ::= timestamp

    val newAfterTs = timestamp + (currentSamplingInterval.getIncrement/2)
    if (newAfterTs <= currentSamplingInterval.getToTimestamp)
      res ::= newAfterTs

    res
  }

  def upsampleAccurate (timestamp: Long, currentSamplingInterval: SamplingInterval): List[Long] = {
    var res = List[Long]()
    res ::= timestamp

    val newBeforeTs = timestamp - (currentSamplingInterval.getIncrement/2)
    if (newBeforeTs >= currentSamplingInterval.getFromTimestamp)
      res ::= newBeforeTs

    val newAfterTs = timestamp + (currentSamplingInterval.getIncrement/2)
    if (newAfterTs <= currentSamplingInterval.getToTimestamp)
      res ::= newAfterTs

    res
  }

  /********************************************************************************************************************/
  /********************************************************************************************************************/
  /********************************************************************************************************************/

  def downsample (timestamp: Long, currentSamplingInterval: SamplingInterval): List[Long] = {
    sampleAccurate(timestamp, currentSamplingInterval.downsample)
  }

}