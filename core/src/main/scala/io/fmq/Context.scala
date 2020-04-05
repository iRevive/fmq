package io.fmq

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import io.fmq.poll.Poller
import io.fmq.socket.pipeline.{Pull, Push}
import io.fmq.socket.pubsub.{Publisher, Subscriber, XPublisher, XSubscriber}
import io.fmq.socket.reqrep.{Dealer, Reply, Request, Router}
import org.zeromq.{SocketType, ZContext, ZMQ}

@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
final class Context[F[_]: Sync: ContextShift] private (private[fmq] val ctx: ZContext, blocker: Blocker) {

  def createSubscriber(topic: Subscriber.Topic): F[Subscriber[F]] =
    createSocket(SocketType.SUB) { socket =>
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

  def createRouter: F[Router[F]] =
    createSocket(SocketType.ROUTER)(socket => new Router(socket, blocker))

  def createDealer: F[Dealer[F]] =
    createSocket(SocketType.DEALER)(socket => new Dealer(socket, blocker))

  def createPoller: Resource[F, Poller[F]] =
    for {
      selector <- Resource.fromAutoCloseableBlocking(blocker)(Sync[F].delay(ctx.getContext.selector()))
      poller   <- Poller.fromSelector[F](selector)
    } yield poller

  def isClosed: F[Boolean] = Sync[F].delay(ctx.isClosed)

  private def createSocket[A](tpe: SocketType)(fa: ZMQ.Socket => A): F[A] =
    Sync[F].delay(fa(ctx.createSocket(tpe)))

}

object Context {

  def create[F[_]: Sync: ContextShift](ioThreads: Int, blocker: Blocker): Resource[F, Context[F]] =
    for {
      ctx <- Resource.fromAutoCloseableBlocking(blocker)(Sync[F].delay(new ZContext(ioThreads)))
    } yield new Context[F](ctx, blocker)

}
