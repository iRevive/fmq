package io.fmq.address

final case class Uri[P <: Protocol, A <: Address] private (protocol: P, address: A) {
  def materialize: String = s"${protocol.name}://${address.value}"
}

object Uri {

  type TCP[A <: Address] = Uri[Protocol.TCP, A]
  type InProc            = Uri[Protocol.InProc, Address.HostOnly]

  def tcp[A <: Address](address: A): Uri.TCP[A] = Uri(Protocol.TCP, address)

  def inproc(address: Address.HostOnly): Uri.InProc = Uri(Protocol.InProc, address)

}

/**
  * The type marker for complete (full) URI.
  *
  * TCP URI must have address and port.
  * InProc URI must have only host.
  */
sealed trait Complete[P <: Protocol, A <: Address]

object Complete {

  implicit object tcpComplete extends Complete[Protocol.TCP, Address.Full]

  implicit object inProcComplete extends Complete[Protocol.InProc, Address.HostOnly]

}
