package io.fmq

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.options.{SocketType, SubscribeTopic}
import io.fmq.poll.Poller
import io.fmq.socket._
import org.zeromq.{ZContext, ZMQ}

final class Context[F[_]: Sync: ContextShift] private (ctx: ZContext, blocker: Blocker) {

  def createSubscriber(topic: SubscribeTopic): Resource[F, Subscriber[F]] =
    for {
      socket <- createSocket(SocketType.Sub)
      _      <- subscribe(socket, topic.value)
    } yield new Subscriber(topic, socket, blocker)

  def createPublisher: Resource[F, Publisher[F]] =
    for {
      socket <- createSocket(SocketType.Pub)
    } yield new Publisher(socket, blocker)

  def createPull: Resource[F, Pull[F]] =
    for {
      socket <- createSocket(SocketType.Pull)
    } yield new Pull[F](socket, blocker)

  def createPush: Resource[F, Push[F]] =
    for {
      socket <- createSocket(SocketType.Push)
    } yield new Push(socket, blocker)

  def createPoller: Resource[F, Poller[F]] =
    Poller.create[F](ctx)

  def isClosed: F[Boolean] = Sync[F].delay(ctx.isClosed)

  private def createSocket(tpe: SocketType): Resource[F, ZMQ.Socket] =
    Resource.liftF(blocker.delay(ctx.createSocket(tpe.zmqType)))

  private def subscribe(socket: ZMQ.Socket, topic: Array[Byte]): Resource[F, Unit] = {
    val acquire = blocker.delay(socket.subscribe(topic)).void
    val release = blocker.delay(socket.unsubscribe(topic)).void

    Resource.make(acquire)(_ => release)
  }

}

object Context {

  def apply[F[_]](implicit ev: Context[F]): Context[F] = ev

  def create[F[_]: Sync: ContextShift](ioThreads: Int, blocker: Blocker): Resource[F, Context[F]] =
    for {
      ctx <- Resource.make(blocker.delay(new ZContext(ioThreads)))(ctx => blocker.delay(ctx.close()))
    } yield new Context[F](ctx, blocker)

}
