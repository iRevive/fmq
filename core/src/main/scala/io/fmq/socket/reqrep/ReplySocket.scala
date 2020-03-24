package io.fmq.socket
package reqrep

import cats.effect.Sync
import io.fmq.address.{Address, Complete, Protocol, Uri}
import org.zeromq.ZMQ

class ReplySocket[F[_], P <: Protocol, A <: Address] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    val uri: Uri[P, A]
)(implicit protected val F: Sync[F], protected val complete: Complete[P, A])
    extends ConnectedSocket[P, A]
    with ProducerSocket[F, P, A]
    with ConsumerSocket[F, P, A]

object ReplySocket extends SocketTypeAlias[ReplySocket]
