package io.fmq
package socket

import cats.effect.{IO, Resource, Sync}
import io.fmq.domain.{Port, Protocol, SubscribeTopic}
import io.fmq.socket.SocketBehavior.SocketResource

class PubSubSpec extends IOSpec with SocketBehavior {

  "PubSub[IO]" should {
    behave like socketSpec[IO, Publisher[IO], Subscriber[IO]](mkSocketResource[IO])
  }

  private def mkSocketResource[F[_]: Sync]: SocketResource[F, F, Publisher[F], Subscriber[F]] =
    new SocketResource[F, F, Publisher[F], Subscriber[F]] {

      override def createProducer(context: Context[F]): Resource[F, Publisher[F]] =
        context.createPublisher

      override def createConsumer(context: Context[F]): Resource[F, Subscriber[F]] =
        context.createSubscriber(SubscribeTopic.All)

      override def bind(producer: Publisher[F], consumer: Subscriber[F], port: Port): Resource[F, SocketResource.Pair[F]] = {
        val address = Protocol.tcp("localhost", port)

        for {
          p <- producer.bind(address)
          c <- consumer.connect(address)
        } yield SocketResource.Pair(p, c)
      }

      override def bindToRandomPort(producer: Publisher[F], consumer: Subscriber[F]): Resource[F, SocketResource.Pair[F]] = {
        val address = Protocol.tcp("localhost")

        for {
          p <- producer.bindToRandomPort(address)
          c <- consumer.connect(Protocol.tcp("localhost", p.port))
        } yield SocketResource.Pair(p, c)
      }

    }

}
