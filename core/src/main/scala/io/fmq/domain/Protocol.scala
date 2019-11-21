package io.fmq.domain

sealed trait Protocol

object Protocol {

  sealed trait tcp extends Protocol

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  object tcp {
    def apply(host: String): Host                 = Host(host)
    def apply(host: String, port: Port): HostPort = HostPort(host, port)

    final case class Host(host: String)                 extends tcp
    final case class HostPort(host: String, port: Port) extends tcp
  }

  def materialize(protocol: Protocol): String = protocol match {
    case tcp.Host(host)                 => s"tcp://$host"
    case tcp.HostPort(host, Port(port)) => s"tcp://$host:${port.toString}"
  }

}
