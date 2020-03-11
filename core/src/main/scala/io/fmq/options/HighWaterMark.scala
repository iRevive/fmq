package io.fmq.options

/**
  * The ZMQ_HWM option shall set the high water mark for the specified socket.
  * The high water mark is a hard limit on the maximum number of outstanding messages ØMQ shall queue in memory
  * for any single peer that the specified socket is communicating with.
  *
  * If this limit has been reached the socket shall enter an exceptional state and depending on the socket type,
  * ØMQ shall take appropriate action such as blocking or dropping sent messages.
  */
sealed abstract class HighWaterMark(val value: Int)

object HighWaterMark {

  final case object NoLimit extends HighWaterMark(0)

  final case class Limit(limit: Int) extends HighWaterMark(limit)

  def fromInt(value: Int): HighWaterMark = value match {
    case 0     => NoLimit
    case other => Limit(other)
  }

}
