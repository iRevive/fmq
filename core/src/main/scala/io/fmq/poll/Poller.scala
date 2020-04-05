package io.fmq.poll

import java.nio.channels.Selector

import cats.effect.concurrent.Ref
import cats.effect.{Resource, Sync}
import cats.instances.list._
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import io.fmq.address.{Address, Protocol}
import io.fmq.socket.{ConsumerSocket, ProducerSocket}
import zmq.poll.{PollItem => ZPollItem}

final class Poller[F[_]: Sync] private (itemsRef: Ref[F, List[PollEntry[F]]], private[fmq] val selector: Selector) {

  def registerConsumer[P <: Protocol, A <: Address](socket: ConsumerSocket[F], handler: ConsumerHandler[F]): F[Unit] =
    itemsRef.update(_ :+ PollEntry.Read(socket, handler))

  def registerProducer[P <: Protocol, A <: Address](socket: ProducerSocket[F], handler: ProducerHandler[F]): F[Unit] =
    itemsRef.update(_ :+ PollEntry.Write(socket, handler))

  /**
    * In the case of [[PollTimeout.Infinity]] the thread will be '''blocked''' until at least one socket
    * can either receive or send a message (based on the socket type).
    *
    * @return total number of available events
    */
  def poll(timeout: PollTimeout): F[Int] =
    for {
      items   <- itemsRef.get
      polling <- items.map(item => (item, toZmqPollItem(item))).pure[F]
      events  <- Sync[F].delay(zmq.ZMQ.poll(selector, polling.toMap.values.toArray, timeout.value))
      _       <- polling.traverse((dispatchItem _).tupled)
    } yield events

  private def dispatchItem(entity: PollEntry[F], item: ZPollItem): F[Unit] = {
    val availableEvents = item.readyOps()

    Sync[F].whenA(availableEvents > 0) {
      entity match {
        case PollEntry.Read(socket, handler)  => handler.run(socket)
        case PollEntry.Write(socket, handler) => handler.run(socket)
      }
    }
  }

  private def toZmqPollItem(pollItem: PollEntry[F]): ZPollItem =
    pollItem match {
      case PollEntry.Read(socket, _)  => new ZPollItem(socket.socket.base, zmq.ZMQ.ZMQ_POLLIN)
      case PollEntry.Write(socket, _) => new ZPollItem(socket.socket.base, zmq.ZMQ.ZMQ_POLLOUT)
    }

}

object Poller {

  def fromSelector[F[_]: Sync](selector: Selector): Resource[F, Poller[F]] =
    for {
      items <- Resource.liftF(Ref.of[F, List[PollEntry[F]]](List.empty))
    } yield new Poller[F](items, selector)

}
