package io.fmq
package socket
package pipeline

import cats.effect.{Resource, Sync}
import io.fmq.address._
import io.fmq.socket.SocketBehavior.SocketResource

import scala.util.Random

object PushPullSpec extends IOSpec with SocketBehavior {

  socketSpec("TCP protocol", tcpSocketResource)
  socketSpec("inproc protocol", inprocSocketResource)

  private def tcpSocketResource[F[_]: Sync]: PushPullResource[F] =
    new PushPullResource[F] {

      override def bind(push: Push[F], pull: Pull[F], port: Int): Resource[F, Pair] = {
        val uri = Uri.Complete.TCP(Address.Full("localhost", port))

        for {
          consumer <- pull.bind(uri)
          producer <- push.connect(uri)
        } yield SocketResource.Pair(producer, consumer)
      }

      override def bindToRandom(push: Push[F], pull: Pull[F]): Resource[F, Pair] = {
        val uri = Uri.Incomplete.TCP(Address.HostOnly("localhost"))

        for {
          consumer <- pull.bindToRandomPort(uri)
          producer <- push.connect(consumer.uri)
        } yield SocketResource.Pair(producer, consumer)
      }

      override def expectedRandomUri(port: Int): Uri.Complete =
        Uri.Complete.TCP(Address.Full("localhost", port))

    }

  private def inprocSocketResource[F[_]: Sync]: PushPullResource[F] =
    new PushPullResource[F] {

      override def bind(push: Push[F], pull: Pull[F], port: Int): Resource[F, Pair] = {
        val uri = Uri.Complete.InProc(Address.HostOnly(s"localhost-$port"))

        for {
          consumer <- pull.bind(uri)
          producer <- push.connect(uri)
        } yield SocketResource.Pair(producer, consumer)
      }

      override def bindToRandom(push: Push[F], pull: Pull[F]): Resource[F, Pair] = {
        val uri = Uri.Complete.InProc(Address.HostOnly(Random.alphanumeric.take(10).mkString))

        for {
          consumer <- pull.bind(uri)
          producer <- push.connect(consumer.uri)
        } yield SocketResource.Pair(producer, consumer)
      }

      override def expectedRandomUri(port: Int): Uri.Complete =
        Uri.Complete.InProc(Address.HostOnly(s"localhost-$port"))

    }

  abstract class PushPullResource[F[_]] extends SocketResource[F, Push[F], Pull[F]] {

    override def createProducer(context: Context[F]): F[Push[F]] =
      context.createPush

    override def createConsumer(context: Context[F]): F[Pull[F]] =
      context.createPull

  }

}
