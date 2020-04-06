package io.fmq.proxy

import io.fmq.socket.ProducerSocket
import io.fmq.socket.pipeline.Push
import io.fmq.socket.pubsub.Publisher
import io.fmq.socket.reqrep.Dealer

final class Control[F[_]] private (private[fmq] val socket: ProducerSocket[F])

object Control {

  def publisher[F[_]](publisher: Publisher.Socket[F]): Control[F] = new Control[F](publisher)

  def dealer[F[_]](dealer: Dealer.Socket[F]): Control[F] = new Control[F](dealer)

  def push[F[_]](push: Push.Socket[F]): Control[F] = new Control[F](push)

}
