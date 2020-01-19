package io.fmq

import cats.data.Kleisli
import io.fmq.address.{Address, Protocol}
import io.fmq.socket.{ConsumerSocket, ProducerSocket}

package object poll {

  type ConsumerHandler[F[_], P <: Protocol, A <: Address] = Kleisli[F, ConsumerSocket[F, P, A], Unit]
  type ProducerHandler[F[_], P <: Protocol, A <: Address] = Kleisli[F, ProducerSocket[F, P, A], Unit]

}
