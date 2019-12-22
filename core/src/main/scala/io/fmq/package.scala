package io

package object fmq {

  type ConnectionIO[A] = free.ConnectionIO.ConnectionIO[A]
  val ConnectionIO: free.ConnectionIO.type = free.ConnectionIO

  type Interpreter[F[_]] = free.Interpreter.Interpreter[F]
  val Interpreter: free.Interpreter.type = free.Interpreter

  type ConsumerSocket[F[_]] = socket.ConsumerSocket[F]
  type ProducerSocket[F[_]] = socket.ProducerSocket[F]

}
