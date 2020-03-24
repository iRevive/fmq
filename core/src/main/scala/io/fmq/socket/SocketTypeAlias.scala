package io.fmq.socket

import io.fmq.address.{Address, Protocol}

private[socket] trait SocketTypeAlias[Socket[_[_], _ <: Protocol, _ <: Address]] {

  type TCP[F[_]]    = Socket[F, Protocol.TCP, Address.Full]
  type InProc[F[_]] = Socket[F, Protocol.InProc, Address.HostOnly]

}
