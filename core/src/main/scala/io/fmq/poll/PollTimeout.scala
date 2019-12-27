package io.fmq.poll

import scala.concurrent.duration.FiniteDuration

sealed abstract class PollTimeout(val value: Long)

object PollTimeout {

  /**
    * Wait infinity for the new message
    */
  final case object Infinity extends PollTimeout(-1L)

  /**
    * Wait fixed duration for the new message
    */
  final case class Fixed(duration: FiniteDuration) extends PollTimeout(duration.toMillis)

}
