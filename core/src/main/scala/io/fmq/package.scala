package io

package object fmq {

  type ConsumerSocket[F[_]] = socket.ConsumerSocket[F]
  type ProducerSocket[F[_]] = socket.ProducerSocket[F]

}
