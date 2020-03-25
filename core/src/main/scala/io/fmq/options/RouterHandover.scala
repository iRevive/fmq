package io.fmq.options

/**
  * If two clients use the same identity when connecting to a ROUTER,
  * the results shall depend on the ZMQ_ROUTER_HANDOVER option setting.
  * If that is not set (or set to the default of false),
  * the ROUTER socket shall reject clients trying to connect with an already-used identity.
  * If that option is set to true, the ROUTER socket shall hand-over the connection to the new client and disconnect the existing one.
  */
sealed abstract class RouterHandover(val value: Boolean)

object RouterHandover {

  /**
    * The ROUTER socket shall hand-over the connection to the new client and disconnect the existing one
    */
  final case object Handover extends RouterHandover(true)

  /**
    * Default. The ROUTER socket shall reject clients trying to connect with an already-used identity
    */
  final case object NoHandover extends RouterHandover(false)

}
