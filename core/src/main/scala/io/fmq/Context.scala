package io.fmq

import cats.effect.kernel.Async
import cats.effect.{Resource, Sync}
import io.fmq.poll.Poller
import io.fmq.proxy.Proxy
import io.fmq.socket.pipeline.{Pull, Push}
import io.fmq.socket.pubsub.{Publisher, Subscriber, XPublisher, XSubscriber}
import io.fmq.socket.reqrep.{Dealer, Reply, Request, Router}
import org.zeromq.{SocketType, ZContext, ZMQ}

final class Context[F[_]: Async] private(private[fmq] val ctx: ZContext) {

  def createSubscriber(topic: Subscriber.Topic): F[Subscriber[F]] =
    createSocket(SocketType.SUB) { socket =>
      val _ = socket.subscribe(topic.value)
      new Subscriber[F](topic, socket)
    }

  def createPublisher: F[Publisher[F]] =
    createSocket(SocketType.PUB)(socket => new Publisher[F](socket))

  def createXSubscriber: F[XSubscriber[F]] =
    createSocket(SocketType.XSUB)(socket => new XSubscriber(socket))

  def createXPublisher: F[XPublisher[F]] =
    createSocket(SocketType.XPUB)(socket => new XPublisher(socket))

  def createPull: F[Pull[F]] =
    createSocket(SocketType.PULL)(socket => new Pull(socket))

  def createPush: F[Push[F]] =
    createSocket(SocketType.PUSH)(socket => new Push(socket))

  def createRequest: F[Request[F]] =
    createSocket(SocketType.REQ)(socket => new Request(socket))

  def createReply: F[Reply[F]] =
    createSocket(SocketType.REP)(socket => new Reply(socket))

  def createRouter: F[Router[F]] =
    createSocket(SocketType.ROUTER)(socket => new Router(socket))

  def createDealer: F[Dealer[F]] =
    createSocket(SocketType.DEALER)(socket => new Dealer(socket))

  def createPoller: Resource[F, Poller[F]] =
    for {
      selector <- Resource.fromAutoCloseable(Sync[F].delay(ctx.getContext.selector()))
    } yield Poller.fromSelector[F](selector)

  val proxy: Proxy[F] = new Proxy[F](this)

  def isClosed: F[Boolean] = Sync[F].delay(ctx.isClosed)

  private def createSocket[A](tpe: SocketType)(fa: ZMQ.Socket => A): F[A] =
    Sync[F].delay(fa(ctx.createSocket(tpe)))

}

object Context {

  def create[F[_]: Async](ioThreads: Int): Resource[F, Context[F]] =
    for {
      ctx <- Resource.fromAutoCloseable(Async[F].delay(new ZContext(ioThreads)))
    } yield new Context[F](ctx)

}
