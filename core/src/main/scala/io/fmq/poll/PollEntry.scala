package io.fmq.poll

import io.fmq.socket.{ConsumerSocket, ProducerSocket}

private[poll] sealed trait PollEntry[F[_]]

private[poll] object PollEntry {

  final case class Read[F[_]](
      socket: ConsumerSocket[F],
      handler: ConsumerHandler[F]
  ) extends PollEntry[F]

  final case class Write[F[_]](
      socket: ProducerSocket[F],
      handler: ProducerHandler[F]
  ) extends PollEntry[F]

}
