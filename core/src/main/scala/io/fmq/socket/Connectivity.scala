package io.fmq.socket

import cats.effect.{ContextShift, Sync}
import io.fmq.socket.api.{BindApi, ConnectApi, SocketFactory}

object Connectivity {

  abstract class All[F[_], Socket[_[_]]](
      protected implicit val CS: ContextShift[F],
      protected implicit val F: Sync[F],
      protected implicit val SF: SocketFactory[Socket]
  ) extends BindApi[F, Socket]
      with ConnectApi[F, Socket]

  abstract class Bind[F[_]: ContextShift, Socket[_[_]]: SocketFactory](
      protected implicit val CS: ContextShift[F],
      protected implicit val F: Sync[F],
      protected implicit val SF: SocketFactory[Socket]
  ) extends BindApi[F, Socket]

  abstract class Connect[F[_]: ContextShift, Socket[_[_]]: SocketFactory](
      protected implicit val CS: ContextShift[F],
      protected implicit val F: Sync[F],
      protected implicit val SF: SocketFactory[Socket]
  ) extends ConnectApi[F, Socket]

}
