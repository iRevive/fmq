package io.fmq.socket

import cats.effect.Sync
import io.fmq.address.{Address, IsComplete, Protocol, Uri}
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SocketOptions}
import org.zeromq.ZMQ

final class ConsumerSocket[F[_]: Sync, P <: Protocol, A <: Address: IsComplete[P, *]] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    val uri: Uri[P, A]
) extends SocketOptions[F]
    with CommonOptions.Get[F]
    with ReceiveOptions.Get[F] {

  override protected def F: Sync[F] = implicitly[Sync[F]]

  /**
    * The operation blocks a thread until a new message is available.
    *
    * Use `blocker.blockOn(socket.recv)` or consume messages on a blocking context in the background:
    *
    * {{{
    * import cats.effect.syntax.concurrent._
    * import cats.effect.{Blocker, Concurrent, ContextShift, Resource}
    * import fs2.Stream
    * import fs2.concurrent.Queue
    * import io.fmq.ConsumerSocket
    *
    * def consume[F[_]: Concurrent: ContextShift](blocker: Blocker, socket: ConsumerSocket[F]): Stream[F, Array[Byte]] = {
    *   def process(queue: Queue[F, Array[Byte]]) =
    *     blocker.blockOn(Stream.repeatEval(socket.recv).through(queue.enqueue).compile.drain)
    *
    *   for {
    *     queue  <- Stream.eval(Queue.unbounded[F, Array[Byte]])
    *     _      <- Stream.resource(Resource.make(process(queue).start)(_.cancel))
    *     result <- queue.dequeue
    *   } yield result
    * }
    * }}}
    */
  def recv: F[Array[Byte]] = Sync[F].delay(socket.recv())

  /**
    * The method blocks the thread until a new message is available.
    * @see [[recv]]
    */
  def recvString: F[String] = Sync[F].delay(socket.recvStr())

  def hasReceiveMore: F[Boolean] = F.delay(socket.hasReceiveMore)

}

object ConsumerSocket {
  type TCP[F[_]]    = ConsumerSocket[F, Protocol.TCP, Address.Full]
  type InProc[F[_]] = ConsumerSocket[F, Protocol.InProc, Address.HostOnly]
}
