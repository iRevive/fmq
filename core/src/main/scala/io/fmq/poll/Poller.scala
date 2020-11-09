package io.fmq.poll

import java.nio.channels.Selector

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import zmq.poll.{PollItem => ZPollItem}

final class Poller[F[_]: Sync] private (private[fmq] val selector: Selector) {

  /**
    * In the case of [[PollTimeout.Infinity]] the thread will be '''blocked''' until at least one socket
    * can either receive or send a message (based on the socket type).
    *
    * @return total number of available events
    */
  def poll(items: NonEmptyList[PollEntry[F]], timeout: PollTimeout): F[Int] =
    for {
      polling <- items.map(item => (item, toZmqPollItem(item))).toList.pure[F]
      events  <- Sync[F].blocking(zmq.ZMQ.poll(selector, polling.toMap.values.toArray, timeout.value))
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

  def fromSelector[F[_]: Sync](selector: Selector): Poller[F] =
    new Poller[F](selector)

}
