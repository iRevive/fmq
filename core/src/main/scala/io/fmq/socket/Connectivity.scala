package io.fmq.socket

import cats.effect.kernel.Sync
import io.fmq.socket.api.{BindApi, ConnectApi, SocketFactory}

object Connectivity {

  abstract class All[F[_], Socket[_[_]]](
      protected implicit val F: Sync[F],
      protected implicit val SF: SocketFactory[Socket]
  ) extends BindApi[F, Socket]
      with ConnectApi[F, Socket]

}
