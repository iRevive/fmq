package io.fmq
package socket
package pipeline

import cats.effect.{IO, Resource, Sync}
import io.fmq.address._
import io.fmq.socket.SocketBehavior.SocketResource

import scala.util.Random

class PushPullSpec extends IOSpec with SocketBehavior {

  "PushPull[IO]" when afterWord("protocol is") {

    "tcp" should {
      behave like socketSpec(tcpSocketResource[IO])
    }

    "inproc" should {
      behave like socketSpec(inprocSocketResource[IO])
    }

  }

  private def tcpSocketResource[F[_]: Sync]: PushPullResource[F] =
    new PushPullResource[F] {

      override def bind(push: Push[F], pull: Pull[F], port: Port): Resource[F, Pair] = {
        val uri = Uri.Complete.TCP(Address.Full(Host.Fixed("localhost"), port))

        for {
          consumer <- pull.bind(uri)
          producer <- push.connect(uri)
        } yield SocketResource.Pair(producer, consumer)
      }

      override def bindToRandom(push: Push[F], pull: Pull[F]): Resource[F, Pair] = {
        val uri = Uri.Incomplete.TCP(Address.HostOnly(Host.Fixed("localhost")))

        for {
          consumer <- pull.bindToRandomPort(uri)
          producer <- push.connect(consumer.uri)
        } yield SocketResource.Pair(producer, consumer)
      }

      override def expectedRandomUri(port: Port): Uri.Complete =
        Uri.Complete.TCP(Address.Full(Host.Fixed("localhost"), port))

    }

  private def inprocSocketResource[F[_]: Sync]: PushPullResource[F] =
    new PushPullResource[F] {

      override def bind(push: Push[F], pull: Pull[F], port: Port): Resource[F, Pair] = {
        val uri = Uri.Complete.InProc(Address.HostOnly(Host.Fixed(s"localhost-$port")))

        for {
          consumer <- pull.bind(uri)
          producer <- push.connect(uri)
        } yield SocketResource.Pair(producer, consumer)
      }

      override def bindToRandom(push: Push[F], pull: Pull[F]): Resource[F, Pair] = {
        val uri = Uri.Complete.InProc(Address.HostOnly(Host.Fixed(Random.alphanumeric.take(10).mkString)))

        for {
          consumer <- pull.bind(uri)
          producer <- push.connect(consumer.uri)
        } yield SocketResource.Pair(producer, consumer)
      }

      override def expectedRandomUri(port: Port): Uri.Complete =
        Uri.Complete.InProc(Address.HostOnly(Host.Fixed(s"localhost-$port")))

    }

  abstract class PushPullResource[F[_]] extends SocketResource[F, Push[F], Pull[F]] {

    override def createProducer(context: Context[F]): Resource[F, Push[F]] =
      context.createPush

    override def createConsumer(context: Context[F]): Resource[F, Pull[F]] =
      context.createPull

  }

}
