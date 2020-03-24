package io.fmq.socket
package pubsub

import cats.effect.Sync
import io.fmq.address.Uri
import org.zeromq.ZMQ

final class XPublisherSocket[F[_]] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    val uri: Uri.Complete
)(implicit protected val F: Sync[F])
    extends ConnectedSocket
    with ProducerSocket[F]
    with ConsumerSocket[F]
