package io.fmq

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import io.fmq.poll.Poller
import io.fmq.socket.pipeline.{Pull, Push}
import io.fmq.socket.pubsub.{Publisher, Subscriber, XPublisher, XSubscriber}
import io.fmq.socket.reqrep.{Reply, Request}
import org.zeromq.{SocketType, ZContext, ZMQ}

final class Context[F[_]: Sync: ContextShift] private (ctx: ZContext, blocker: Blocker) {

  def createSubscriber(topic: Subscriber.Topic): F[Subscriber[F]] =
    createSocket(SocketType.PUB) { socket =>
      val _ = socket.subscribe(topic.value)
      new Subscriber[F](topic, socket, blocker)
    }

  def createPublisher: F[Publisher[F]] =
    createSocket(SocketType.PUB)(socket => new Publisher[F](socket, blocker))

  def createXSubscriber: F[XSubscriber[F]] =
    createSocket(SocketType.XSUB)(socket => new XSubscriber(socket, blocker))

  def createXPublisher: F[XPublisher[F]] =
    createSocket(SocketType.XPUB)(socket => new XPublisher(socket, blocker))

  def createPull: F[Pull[F]] =
    createSocket(SocketType.PULL)(socket => new Pull(socket, blocker))

  def createPush: F[Push[F]] =
    createSocket(SocketType.PUSH)(socket => new Push(socket, blocker))

  def createRequest: F[Request[F]] =
    createSocket(SocketType.REQ)(socket => new Request(socket, blocker))

  def createReply: F[Reply[F]] =
    createSocket(SocketType.REP)(socket => new Reply(socket, blocker))

  def createPoller: Resource[F, Poller[F]] =
    Poller.create[F](ctx)

  def isClosed: F[Boolean] = Sync[F].delay(ctx.isClosed)

  private def createSocket[A](tpe: SocketType)(fa: ZMQ.Socket => A): F[A] =
    Sync[F].delay(fa(ctx.createSocket(tpe)))

}

object Context {

  def apply[F[_]](implicit ev: Context[F]): Context[F] = ev

  def create[F[_]: Sync: ContextShift](ioThreads: Int, blocker: Blocker): Resource[F, Context[F]] =
    for {
      ctx <- Resource.make(blocker.delay(new ZContext(ioThreads)))(ctx => blocker.delay(ctx.close()))
    } yield new Context[F](ctx, blocker)

}
