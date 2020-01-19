package io.fmq.poll

import io.fmq.address.{Address, Protocol}
import io.fmq.socket.{ConsumerSocket, ProducerSocket}

private[poll] sealed trait PollEntry[F[_]]

private[poll] object PollEntry {

  final case class Read[F[_], P <: Protocol, A <: Address](
      socket: ConsumerSocket[F, P, A],
      handler: ConsumerHandler[F, P, A]
  ) extends PollEntry[F]

  final case class Write[F[_], P <: Protocol, A <: Address](
      socket: ProducerSocket[F, P, A],
      handler: ProducerHandler[F, P, A]
  ) extends PollEntry[F]

}
