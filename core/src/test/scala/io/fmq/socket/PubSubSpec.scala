package io.fmq
package socket

import cats.effect.{IO, Resource, Sync}
import io.fmq.address.{Address, Host, Port, Uri}
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
        context.createSubscriber(Subscriber.Topic.All)

      override def bind(producer: Publisher[F], consumer: Subscriber[F], port: Port): Resource[F, SocketResource.Pair[F]] = {
        val uri = Uri.tcp(Address.Full(Host.Fixed("localhost"), port))

        for {
          p <- producer.bind(uri)
          c <- consumer.connect(uri)
        } yield SocketResource.Pair(p, c)
      }

      override def bindToRandomPort(producer: Publisher[F], consumer: Subscriber[F]): Resource[F, SocketResource.Pair[F]] = {
        val uri = Uri.tcp(Address.HostOnly(Host.Fixed("localhost")))

        for {
          p <- producer.bindToRandomPort(uri)
          c <- consumer.connect(p.uri)
        } yield SocketResource.Pair(p, c)
      }

    }

}
