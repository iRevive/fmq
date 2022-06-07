package io.fmq.poll

import java.io.IOException
import java.nio.channels._
import java.util.concurrent.TimeUnit

import cats.Monad
import cats.data.NonEmptyList
import cats.effect.kernel.Sync
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.order._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import zmq.ZError
import zmq.poll.{PollItem => ZPollItem}

import scala.jdk.CollectionConverters._

final class Poller[F[_]: Sync] private (private[fmq] val selector: Selector) {

  /**
    * In the case of [[PollTimeout.Infinity]] the thread will be '''blocked''' until at least one socket
    * can either receive or send a message (according to the type of a socket).
    *
    * @return total number of available events
    */
  def poll(items: NonEmptyList[PollEntry[F]], timeout: PollTimeout): F[Int] =
    for {
      polling <- items.map(item => (item, toZmqPollItem(item))).toList.pure[F]
      events  <- pollInternal(polling.toMap.values.toList, timeout)
      _       <- polling.traverse((dispatchItem _).tupled)
    } yield events

  private def pollInternal(items: List[ZPollItem], timeout: PollTimeout): F[Int] =
    (Sync[F].delay(attachItemsToSelector(selector, items)) >> doPoll(timeout)).recoverWith {
      case _: ClosedSelectorException => Sync[F].pure(-1)
      case _: ClosedChannelException  => Sync[F].pure(-1)
      case e: IOException             => Sync[F].raiseError(new ZError.IOException(e))
    }

  private def doPoll(timeout: PollTimeout): F[Int] =
    Monad[F].tailRecM[(Long, Long, Boolean), Int]((0L, 0L, true)) { case (now, end, firstPass) =>
      val waitMillis: Long =
        if (firstPass) 0L
        else if (timeout.value === 0L || timeout === PollTimeout.Infinity) -1L
        else {
          val diff = TimeUnit.NANOSECONDS.toMillis(end - now)
          if (diff === 0L) 1L else diff
        }

      Sync[F].interruptible(readyKeys(waitMillis)).flatMap {
        case nevents if nevents > 0 =>
          Sync[F].pure(Right(nevents))

        case nevents if timeout.value === 0L =>
          Sync[F].pure(Right(nevents))

        //  At this point we are meant to wait for events but there are none.
        //  If timeout is infinite we can just loop until we get some events.
        case _ if timeout === PollTimeout.Infinity =>
          Sync[F].pure(Left((now, end, false)))

        //  The timeout is finite and there are no events. In the first pass
        //  we get a timestamp of when the polling have begun. (We assume that
        //  first pass have taken negligible time). We also compute the time
        //  when the polling should time out.
        case nevents if firstPass =>
          val now = zmq.util.Clock.nowNS()
          val end = now + TimeUnit.MILLISECONDS.toNanos(timeout.value)
          Sync[F].pure(Either.cond(now === end, nevents, (now, end, false)))

        //  Find out whether timeout have expired.
        case nevents =>
          val now = zmq.util.Clock.nowNS()
          Sync[F].pure(Either.cond(now >= end, nevents, (now, end, false)))
      }
    }

  private def attachItemsToSelector(selector: Selector, items: List[ZPollItem]): Unit = {
    val saved = new scala.collection.mutable.HashMap[SelectableChannel, SelectionKey]
    for (key <- selector.keys().asScala if key.isValid) saved.put(key.channel(), key)

    for (item <- items) {
      val ch = item.getChannel // mailbox channel if ZMQ socket
      saved.remove(ch) match {
        case Some(key) =>
          if (key.interestOps() =!= item.interestOps()) {
            val _ = key.interestOps(item.interestOps())
          }
          key.attach(item)

        case None =>
          ch.register(selector, item.interestOps(), item)
      }
    }

    if (saved.nonEmpty) {
      for (deprecated <- saved.values)
        deprecated.cancel()
    }
  }

  private def readyKeys(waitMillis: Long): Int = {

    @SuppressWarnings(Array("org.wartremover.warts.IterableOps", "org.wartremover.warts.AsInstanceOf"))
    @scala.annotation.tailrec
    def loop(keys: Set[SelectionKey], counter: Int, rc: Int): Int =
      if (keys.nonEmpty) {
        val key = keys.head

        val item  = key.attachment().asInstanceOf[ZPollItem]
        val ready = item.readyOps(key, rc)

        if (ready < 0) -1
        else if (ready === 0) loop(keys.tail, counter, rc)
        else loop(keys.tail, counter + 1, rc)
      } else {
        counter
      }

    val rc =
      if (waitMillis < 0) selector.select(0)
      else if (waitMillis === 0) selector.selectNow()
      else selector.select(waitMillis)

    val result = loop(selector.keys().asScala.toSet, 0, rc)
    if (result >= 0) {
      selector.selectedKeys().clear()
    }
    result
  }

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
