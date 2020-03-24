package io.fmq.address

sealed trait Uri {

  def protocol: Protocol

  def address: Address

  final def materialize: String = s"${protocol.name}://${address.value}"
}

object Uri {

  sealed abstract class Incomplete(val protocol: Protocol) extends Uri

  object Incomplete {
    final case class TCP(address: Address.HostOnly) extends Incomplete(Protocol.TCP)
  }

  sealed abstract class Complete(val protocol: Protocol) extends Uri

  object Complete {
    final case class TCP(address: Address.Full)        extends Complete(Protocol.TCP)
    final case class InProc(address: Address.HostOnly) extends Complete(Protocol.InProc)
  }

}
