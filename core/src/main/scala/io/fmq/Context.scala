package io.fmq

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import io.fmq.domain.{SocketType, SubscribeTopic}
import io.fmq.socket._
import io.fmq.socket.internal.Socket
import org.zeromq.ZContext

final class Context[F[_]: ContextShift] private[fmq] (ctx: ZContext, blocker: Blocker)(implicit F: Sync[F]) {

  def createSubscriber(topic: SubscribeTopic): Resource[F, Subscriber[F]] =
    for {
      socket <- createSocket(SocketType.Sub)
      _      <- socket.subscribe(topic.value)
    } yield new Subscriber(topic, socket)

  def createPublisher: Resource[F, Publisher[F]] =
    for {
      socket <- createSocket(SocketType.Pub)
    } yield new Publisher[F](socket)

  def isClosed: F[Boolean] = F.delay(ctx.isClosed)

  private def createSocket(tpe: SocketType): Resource[F, Socket[F]] =
    for {
      socket <- Resource.make(blocker.delay(ctx.createSocket(tpe.zmqType)))(s => blocker.delay(ctx.destroySocket(s)))
    } yield new Socket[F](socket, blocker)

}

object Context {

  def create[F[_]: Sync: ContextShift](ioThreads: Int, blocker: Blocker): Resource[F, Context[F]] =
    for {
      ctx <- Resource.make(blocker.delay(new ZContext(ioThreads)))(ctx => blocker.delay(ctx.close()))
    } yield new Context[F](ctx, blocker)

}
