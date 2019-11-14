package io.fmq.domain

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration

sealed abstract class ReceiveTimeout(val value: Int)

object ReceiveTimeout {

  /**
    * Returns the message or null immediately
    */
  final case object NoDelay extends ReceiveTimeout(0)

  /**
    * Wait infinity for the new message
    */
  final case object WaitUntilAvailable extends ReceiveTimeout(-1)

  /**
    * Wait fixed duration for the new message
    */
  final case class FixedTimeout(duration: FiniteDuration) extends ReceiveTimeout(duration.toMillis.toInt)

  def fromInt(value: Int): ReceiveTimeout = value match {
    case -1    => WaitUntilAvailable
    case 0     => NoDelay
    case other => FixedTimeout(FiniteDuration(other, TimeUnit.MILLISECONDS))
  }

}
