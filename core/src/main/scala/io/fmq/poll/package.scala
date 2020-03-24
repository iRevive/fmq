package io.fmq

import cats.data.Kleisli
import io.fmq.socket.{ConsumerSocket, ProducerSocket}

package object poll {

  type ConsumerHandler[F[_]] = Kleisli[F, ConsumerSocket[F], Unit]
  type ProducerHandler[F[_]] = Kleisli[F, ProducerSocket[F], Unit]

}
