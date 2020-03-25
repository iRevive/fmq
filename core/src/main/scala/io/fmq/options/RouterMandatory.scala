package io.fmq.options

/**
  * Sets the ROUTER socket behavior when an unroutable message is encountered.
  * A value of false is the default and discards the message silently
  * when it cannot be routed or the peers SNDHWM is reached.
  */
sealed abstract class RouterMandatory(val value: Boolean)

object RouterMandatory {

  /**
    * Returns an EHOSTUNREACH error code if the message cannot be routed.
    */
  final case object Mandatory extends RouterMandatory(true)

  /**
    * Default. Discards the message silently when it cannot be routed.
    */
  final case object NonMandatory extends RouterMandatory(false)

}
