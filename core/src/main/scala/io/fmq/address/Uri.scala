package io.fmq.address

final case class Uri[P <: Protocol, A <: Address: IsComplete[P, *]] private (protocol: P, address: A) {
  def isComplete: Boolean = IsComplete[P, A].value
  def materialize: String = s"${protocol.name}://${address.value}"
}

object Uri {

  type TCP[A <: Address] = Uri[Protocol.TCP, A]
  type InProc            = Uri[Protocol.InProc, Address.HostOnly]

  def tcp[A <: Address: IsComplete.TCP](address: A): Uri.TCP[A] = Uri(Protocol.TCP, address)

  def inproc(address: Address.HostOnly): Uri.InProc = Uri(Protocol.InProc, address)

}

sealed abstract class IsComplete[P <: Protocol, A <: Address](val value: Boolean)

object IsComplete {

  type TCP[A <: Address] = IsComplete[Protocol.TCP, A]

  def apply[P <: Protocol, A <: Address](implicit ev: IsComplete[P, A]): IsComplete[P, A] = ev

  implicit val isCompleteTCPFull: IsComplete.TCP[Address.Full] =
    new IsComplete[Protocol.TCP, Address.Full](true) {}

  implicit val isCompleteTCPOnlyHost: IsComplete.TCP[Address.HostOnly] =
    new IsComplete[Protocol.TCP, Address.HostOnly](false) {}

  implicit val isCompleteInProc: IsComplete[Protocol.InProc, Address.HostOnly] =
    new IsComplete[Protocol.InProc, Address.HostOnly](true) {}

}
