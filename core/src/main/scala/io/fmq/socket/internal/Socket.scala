package io.fmq.socket.internal

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.domain._
import org.zeromq.ZMQ

private[fmq] final class Socket[F[_]: Sync: ContextShift](socket: ZMQ.Socket, blocker: Blocker) {

  def connect(address: Address.Const): Resource[F, ConnectedSocket[F]] =
    for {
      _ <- Resource.fromAutoCloseable(blocker.delay(socket.connect(address.materialize)).as(socket))
    } yield new ConnectedSocket[F](this, address.port)

  def bind(address: Address): Resource[F, ConnectedSocket[F]] =
    address match {
      case const: Address.Const       => bindToConstAddress(const)
      case random: Address.RandomPort => bindToRandomPort(random)
    }

  def subscribe(topic: Array[Byte]): Resource[F, Unit] = {
    val acquire = blocker.delay(socket.subscribe(topic)).void
    val release = blocker.delay(socket.unsubscribe(topic)).void

    Resource.make(acquire)(_ => release)
  }

  def recv: F[Array[Byte]]                  = blocker.delay(socket.recv())
  def recvString: F[String]                 = blocker.delay(socket.recvStr())
  def send(bytes: Array[Byte]): F[Unit]     = blocker.delay(socket.send(bytes)).void
  def sendMore(bytes: Array[Byte]): F[Unit] = blocker.delay(socket.sendMore(bytes)).void

  def receiveTimeout: F[ReceiveTimeout] = blocker.delay(ReceiveTimeout.fromInt(socket.getReceiveTimeOut))
  def sendTimeout: F[SendTimeout]       = blocker.delay(SendTimeout.fromInt(socket.getSendTimeOut))
  def linger: F[Linger]                 = blocker.delay(Linger.fromInt(socket.getLinger))
  def identity: F[Identity]             = blocker.delay(Option(socket.getIdentity).fold[Identity](Identity.Empty)(Identity.Fixed))

  def setReceiveTimeout(timeout: ReceiveTimeout): F[Unit] = blocker.delay(socket.setReceiveTimeOut(timeout.value)).void
  def setSendTimeout(timeout: SendTimeout): F[Unit]       = blocker.delay(socket.setSendTimeOut(timeout.value)).void
  def setLinger(linger: Linger): F[Unit]                  = blocker.delay(socket.setLinger(linger.value)).void
  def setIdentity(identity: Identity.Fixed): F[Unit]      = blocker.delay(socket.setIdentity(identity.value)).void

  def hasReceiveMore: F[Boolean] = blocker.delay(socket.hasReceiveMore)

  private def bindToConstAddress(address: Address.Const): Resource[F, ConnectedSocket[F]] =
    for {
      _ <- Resource.fromAutoCloseable(blocker.delay(socket.bind(address.materialize)).as(socket))
    } yield new ConnectedSocket[F](this, address.port)

  private def bindToRandomPort(address: Address.RandomPort): Resource[F, ConnectedSocket[F]] = {
    val acquire = blocker.delay(socket.bindToRandomPort(address.materialize))
    val release = blocker.delay(socket.close())

    for {
      port <- Resource.make(acquire)(_ => release)
    } yield new ConnectedSocket[F](this, Port(port))
  }

}
