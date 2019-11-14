package io.fmq.domain

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration

sealed abstract class SendTimeout(val value: Int)

object SendTimeout {

  /**
    * Does not send message and does not wait if the message cannot be send
    */
  final case object NoDelay extends SendTimeout(0)

  /**
    * Block until the message is sent
    */
  final case object Infinity extends SendTimeout(-1)

  /**
    * Try to send the message for that amount of time before returning error
    */
  final case class FixedTimeout(duration: FiniteDuration) extends SendTimeout(duration.toMillis.toInt)

  def fromInt(value: Int): SendTimeout = value match {
    case -1    => Infinity
    case 0     => NoDelay
    case other => FixedTimeout(FiniteDuration(other, TimeUnit.MILLISECONDS))
  }

}
