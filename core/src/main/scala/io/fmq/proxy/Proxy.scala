package io.fmq
package proxy

import cats.data.{Kleisli, NonEmptyList}
import cats.effect.kernel.{Async, Outcome}
import cats.effect.syntax.async._
import cats.effect.syntax.spawn._
import cats.effect.{Resource, Sync}
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.fmq.poll.{ConsumerHandler, PollEntry, PollTimeout, Poller}
import io.fmq.socket.{BidirectionalSocket, ConsumerSocket, ProducerSocket}

import scala.concurrent.ExecutionContext

@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
final class Proxy[F[_]: Async](ctx: Context[F]) {

  def unidirectional(frontend: ConsumerSocket[F], backend: ProducerSocket[F]): Resource[F, Proxy.Configured[F]] =
    unidirectional(frontend, backend, None)

  def unidirectional(
      frontend: ConsumerSocket[F],
      backend: ProducerSocket[F],
      control: Option[Control[F]]
  ): Resource[F, Proxy.Configured[F]] =
    for {
      poller <- ctx.createPoller
    } yield {
      val items = NonEmptyList.one(
        PollEntry.Read(frontend, forward(backend, control))
      )

      new Proxy.Configured[F](poller, items)
    }

  def bidirectional(frontend: BidirectionalSocket[F], backend: BidirectionalSocket[F]): Resource[F, Proxy.Configured[F]] =
    bidirectional(frontend, backend, None, None)

  def bidirectional(
      frontend: BidirectionalSocket[F],
      backend: BidirectionalSocket[F],
      controlIn: Option[Control[F]],
      controlOut: Option[Control[F]]
  ): Resource[F, Proxy.Configured[F]] =
    for {
      poller <- ctx.createPoller
    } yield {
      val items = NonEmptyList.of(
        PollEntry.Read(frontend, forward(backend, controlIn)),
        PollEntry.Read(backend, forward(frontend, controlOut))
      )

      new Proxy.Configured[F](poller, items)
    }

  private def forward(target: ProducerSocket[F], capture: Option[Control[F]]): ConsumerHandler[F] = {

    val withCapture: (ProducerSocket[F] => F[Unit]) => F[Unit] =
      capture match {
        case Some(c) => f => f(c.socket)
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

  final class Configured[F[_]: Async] private[Proxy](
      poller: Poller[F],
      items: NonEmptyList[PollEntry[F]]
  ) {

    def start(ec: ExecutionContext): Resource[F, F[Outcome[F, Throwable, F[Unit]]]] = {
      val fiber = poller.poll(items, PollTimeout.Infinity).foreverM[Unit]

      Async[F].suspend(Sync.Type.Blocking)(fiber).evalOn(ec).background
    }

  }

}
