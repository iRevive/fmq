package io.fmq
package socket
package pubsub

import cats.effect.{IO, Resource, Sync}
import io.fmq.address._
import io.fmq.socket.SocketBehavior.SocketResource

import scala.util.Random

class PubSubSpec extends IOSpec with SocketBehavior {

  "PubSub[IO]" when afterWord("protocol is") {

    "tcp" should {
      behave like socketSpec(tcpSocketResource[IO])
    }

    "inproc" should {
      behave like socketSpec(inprocSocketResource[IO])
    }

  }

  private def tcpSocketResource[F[_]: Sync]: PubSubResource[F, Protocol.TCP, Address.Full] =
    new PubSubResource[F, Protocol.TCP, Address.Full] {

      override def bind(producer: Publisher[F], consumer: Subscriber[F], port: Port): Resource[F, Pair] = {
        val uri = Uri.tcp(Address.Full(Host.Fixed("localhost"), port))

        for {
          p <- producer.bind(uri)
          c <- consumer.connect(uri)
        } yield SocketResource.Pair(p, c)
      }

      override def bindToRandom(producer: Publisher[F], consumer: Subscriber[F]): Resource[F, Pair] = {
        val uri = Uri.tcp(Address.HostOnly(Host.Fixed("localhost")))

        for {
          p <- producer.bindToRandomPort(uri)
          c <- consumer.connect(p.uri)
        } yield SocketResource.Pair(p, c)
      }

      override def expectedRandomUri(port: Port): Uri[Protocol.TCP, Address.Full] =
        Uri.tcp(Address.Full(Host.Fixed("localhost"), port))

    }

  private def inprocSocketResource[F[_]: Sync]: PubSubResource[F, Protocol.InProc, Address.HostOnly] =
    new PubSubResource[F, Protocol.InProc, Address.HostOnly] {

      override def bind(producer: Publisher[F], consumer: Subscriber[F], port: Port): Resource[F, Pair] = {
        val uri = Uri.inproc(Address.HostOnly(Host.Fixed(s"localhost-$port")))

        for {
          p <- producer.bind(uri)
          c <- consumer.connect(uri)
        } yield SocketResource.Pair(p, c)
      }

      override def bindToRandom(producer: Publisher[F], consumer: Subscriber[F]): Resource[F, Pair] = {
        val uri = Uri.inproc(Address.HostOnly(Host.Fixed(Random.alphanumeric.take(10).mkString)))

        for {
          p <- producer.bind(uri)
          c <- consumer.connect(p.uri)
        } yield SocketResource.Pair(p, c)
      }

      override def expectedRandomUri(port: Port): Uri[Protocol.InProc, Address.HostOnly] =
        Uri.inproc(Address.HostOnly(Host.Fixed(s"localhost-$port")))

    }

  abstract class PubSubResource[F[_], P <: Protocol, A <: Address] extends SocketResource[F, P, A, Publisher[F], Subscriber[F]] {

    override def createProducer(context: Context[F]): Resource[F, Publisher[F]] =
      context.createPublisher

    override def createConsumer(context: Context[F]): Resource[F, Subscriber[F]] =
      context.createSubscriber(Subscriber.Topic.All)

  }

}
