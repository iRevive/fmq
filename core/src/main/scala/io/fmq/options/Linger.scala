package io.fmq.options

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration

/**
  * The linger period determines how long pending messages which have yet to be sent to a peer
  * shall linger in memory after a socket is disconnected with disconnect or closed with close,
  * and further affects the termination of the socket's context with Ctx#term.
  */
sealed abstract class Linger(val value: Int)

object Linger {

  /**
    * Pending messages shall not be discarded after a call to disconnect() or close().
    */
  final case object Infinity extends Linger(-1)

  /**
    * Pending messages shall be discarded immediately after a call to disconnect() or close().
    */
  final case object Immediately extends Linger(0)

  /**
    * Pending messages shall not be discarded after a call to disconnect() or close().
    * attempting to terminate the socket's context with Ctx#term() shall block until either all pending messages
    * have been sent to a peer, or the linger period expires, after which any pending messages shall be discarded.
    */
  final case class Fixed(duration: FiniteDuration) extends Linger(duration.toMillis.toInt)

  def fromInt(value: Int): Linger = value match {
    case -1    => Infinity
    case 0     => Immediately
    case other => Fixed(FiniteDuration(other.toLong, TimeUnit.MILLISECONDS))
  }

}
