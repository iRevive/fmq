package io.fmq.address

import java.net.URI

import cats.instances.int._
import cats.syntax.either._
import cats.syntax.eq._

sealed trait Uri {
  def protocol: Protocol
  def address: Address

  final def materialize: String = s"${protocol.name}://${address.value}"
}

object Uri {

  sealed abstract class Incomplete(val protocol: Protocol) extends Uri

  object Incomplete {

    final case class TCP(address: Address.HostOnly) extends Incomplete(Protocol.TCP)

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def tcpFromStringUnsafe(input: String): TCP =
      tcpFromString(input).valueOr(throw _)

    def tcpFromString(input: String): Either[Throwable, TCP] =
      fromString(input, Protocol.TCP, portAllowed = false)((host, _) => TCP(Address.HostOnly(host)))

  }

  sealed abstract class Complete(val protocol: Protocol) extends Uri

  object Complete {

    final case class TCP(address: Address.Full)        extends Complete(Protocol.TCP)
    final case class InProc(address: Address.HostOnly) extends Complete(Protocol.InProc)

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def inProcFromStringUnsafe(input: String): InProc =
      inProcFromString(input).valueOr(throw _)

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def tcpFromStringUnsafe(input: String): TCP =
      tcpFromString(input).valueOr(throw _)

    def inProcFromString(input: String): Either[Throwable, InProc] =
      fromString(input, Protocol.InProc, portAllowed = false)((host, _) => InProc(Address.HostOnly(host)))

    def tcpFromString(input: String): Either[Throwable, TCP] =
      fromString(input, Protocol.TCP, portAllowed = true)((host, port) => TCP(Address.Full(host, port)))

  }

  private def fromString[A](
      input: String,
      protocol: Protocol,
      portAllowed: Boolean
  )(create: (String, Int) => A): Either[Throwable, A] =
    for {
      uri  <- createJavaUri(input)
      _    <- verifyProtocol(uri.getScheme, protocol)
      host <- verifyHost(uri.getHost)
      port <- verifyPort(uri.getPort, portAllowed)
    } yield create(host, port)

  private def createJavaUri(input: String): Either[Throwable, URI] =
    Either.catchNonFatal(java.net.URI.create(input))

  private def verifyProtocol(scheme: String, expected: Protocol): Either[IllegalArgumentException, Unit] =
    Either.cond(
      Option(scheme).contains(expected.name),
      (),
      new IllegalArgumentException(s"Expected protocol [${expected.name}] current [$scheme]")
    )

  private def verifyHost(host: String): Either[IllegalArgumentException, String] =
    Option(host).toRight(new IllegalArgumentException("Host is missing"))

  private def verifyPort(port: Int, allowed: Boolean): Either[IllegalArgumentException, Int] =
    if (allowed) Either.cond(port =!= -1, port, new IllegalArgumentException("Port is missing"))
    else Either.cond(port === -1, port, new IllegalArgumentException("Port is not allowed"))

}
