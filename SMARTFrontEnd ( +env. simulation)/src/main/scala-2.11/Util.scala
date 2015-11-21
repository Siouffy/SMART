package smart.util.io.util


object Util {

  def toClosestInt(value: Double): Double = {
    val diff = value - value.toInt
    if (diff > 0.5)
      value.toInt + 1
    else
      value.toInt
  }


}
