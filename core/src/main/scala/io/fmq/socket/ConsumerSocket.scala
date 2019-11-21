package io.fmq.socket

import cats.effect.Sync
import io.fmq.domain.Port
import io.fmq.socket.api.ReceiveOptions
import org.zeromq.ZMQ

final class ConsumerSocket[F[_]](
    protected val socket: ZMQ.Socket,
    val port: Port
)(implicit protected val F: Sync[F])
    extends ReceiveOptions[F] {

  def recv: F[Array[Byte]]  = Sync[F].delay(socket.recv())
  def recvString: F[String] = Sync[F].delay(socket.recvStr())

}
