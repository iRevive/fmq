package io.fmq.socket
package pubsub

import cats.effect.Sync
import io.fmq.address.{Address, Complete, Protocol, Uri}
import org.zeromq.ZMQ

class XPublisherSocket[F[_], P <: Protocol, A <: Address] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    val uri: Uri[P, A]
)(implicit protected val F: Sync[F], protected val complete: Complete[P, A])
    extends ConnectedSocket[P, A]
    with ProducerSocket[F, P, A]
    with ConsumerSocket[F, P, A]

object XPublisherSocket {

  type TCP[F[_]]    = XPublisherSocket[F, Protocol.TCP, Address.Full]
  type InProc[F[_]] = XPublisherSocket[F, Protocol.InProc, Address.HostOnly]

}
