package io.fmq
package proxy

import cats.data.Kleisli
import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Sync}
import cats.effect.syntax.concurrent._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.fmq.poll.{ConsumerHandler, PollTimeout, Poller}
import io.fmq.socket.{BidirectionalSocket, ConsumerSocket, ProducerSocket}

final class Proxy[F[_]: Concurrent: ContextShift](ctx: Context[F]) {

  def unidirectional(frontend: ConsumerSocket[F], backend: ProducerSocket[F]): Resource[F, Proxy.Configured[F]] =
    unidirectional(frontend, backend, None)

  def unidirectional(
      frontend: ConsumerSocket[F],
      backend: ProducerSocket[F],
      control: Option[ProducerSocket[F]]
  ): Resource[F, Proxy.Configured[F]] =
    for {
      poller <- ctx.createPoller
      _      <- Resource.liftF(poller.registerConsumer(frontend, forward(backend, control)))
    } yield new Proxy.Configured[F](poller)

  def bidirectional(frontend: BidirectionalSocket[F], backend: BidirectionalSocket[F]): Resource[F, Proxy.Configured[F]] =
    bidirectional(frontend, backend, None, None)

  def bidirectional(
      frontend: BidirectionalSocket[F],
      backend: BidirectionalSocket[F],
      controlIn: Option[ProducerSocket[F]],
      controlOut: Option[ProducerSocket[F]]
  ): Resource[F, Proxy.Configured[F]] =
    for {
      poller <- ctx.createPoller
      _      <- Resource.liftF(poller.registerConsumer(frontend, forward(backend, controlIn)))
      _      <- Resource.liftF(poller.registerConsumer(backend, forward(frontend, controlOut)))
    } yield new Proxy.Configured[F](poller)

  private def forward(target: ProducerSocket[F], capture: Option[ProducerSocket[F]]): ConsumerHandler[F] = {

    val withCapture: (ProducerSocket[F] => F[Unit]) => F[Unit] =
      capture match {
        case Some(c) => f => f(c)
        case None    => _ => Sync[F].unit
      }

    def send(message: Array[Byte], socket: ConsumerSocket[F]): F[Unit] =
      socket.hasReceiveMore.ifM(
        target.sendMore(message) *> withCapture(_.sendMore(message)) *> loop(socket),
        target.send(message) >> withCapture(_.send(message))
      )

    def loop(socket: ConsumerSocket[F]): F[Unit] =
      for {
        message <- socket.receive[Array[Byte]]
        _       <- send(message, socket)
      } yield ()

    Kleisli(from => loop(from))
  }

}

object Proxy {

  final class Configured[F[_]: Concurrent: ContextShift] private[Proxy] (poller: Poller[F]) {

    def start(blocker: Blocker): Resource[F, F[Unit]] =
      blocker.blockOn(poller.poll(PollTimeout.Infinity).foreverM[Unit]).background
  }

}
