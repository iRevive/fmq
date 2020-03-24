package io.fmq

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import io.fmq.poll.Poller
import io.fmq.socket.pipeline.{Pull, Push}
import io.fmq.socket.pubsub.{Publisher, Subscriber, XPublisher, XSubscriber}
import org.zeromq.{SocketType, ZContext, ZMQ}

final class Context[F[_]: Sync: ContextShift] private (ctx: ZContext, blocker: Blocker) {

  def createSubscriber(topic: Subscriber.Topic): Resource[F, Subscriber[F]] =
    for {
      socket <- createSocket(SocketType.SUB)
      _      <- Resource.liftF(Sync[F].delay(socket.subscribe(topic.value)))
    } yield new Subscriber(topic, socket, blocker)

  def createPublisher: Resource[F, Publisher[F]] =
    for {
      socket <- createSocket(SocketType.PUB)
    } yield new Publisher(socket, blocker)

  def createXSubscriber: Resource[F, XSubscriber[F]] =
    for {
      socket <- createSocket(SocketType.XSUB)
    } yield new XSubscriber(socket, blocker)

  def createXPublisher: Resource[F, XPublisher[F]] =
    for {
      socket <- createSocket(SocketType.XPUB)
    } yield new XPublisher(socket, blocker)

  def createPull: Resource[F, Pull[F]] =
    for {
      socket <- createSocket(SocketType.PULL)
    } yield new Pull[F](socket, blocker)

  def createPush: Resource[F, Push[F]] =
    for {
      socket <- createSocket(SocketType.PUSH)
    } yield new Push(socket, blocker)

  def createPoller: Resource[F, Poller[F]] =
    Poller.create[F](ctx)

  def isClosed: F[Boolean] = Sync[F].delay(ctx.isClosed)

  private def createSocket(tpe: SocketType): Resource[F, ZMQ.Socket] =
    Resource.liftF(blocker.delay(ctx.createSocket(tpe)))

}

object Context {

  def apply[F[_]](implicit ev: Context[F]): Context[F] = ev

  def create[F[_]: Sync: ContextShift](ioThreads: Int, blocker: Blocker): Resource[F, Context[F]] =
    for {
      ctx <- Resource.make(blocker.delay(new ZContext(ioThreads)))(ctx => blocker.delay(ctx.close()))
    } yield new Context[F](ctx, blocker)

}
