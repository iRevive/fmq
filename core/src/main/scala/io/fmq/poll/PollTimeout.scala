package io.fmq.poll

import cats.Eq

import scala.concurrent.duration.FiniteDuration

sealed abstract class PollTimeout(val value: Long)

object PollTimeout {

  /**
    * Wait until new event is available
    */
  final case object Infinity extends PollTimeout(-1L)

  /**
    * Wait fixed duration until new event is available
    */
  final case class Fixed(duration: FiniteDuration) extends PollTimeout(duration.toMillis)

  implicit val pollTimeoutEq: Eq[PollTimeout] = Eq.fromUniversalEquals

}
