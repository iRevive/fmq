package io.fmq

package object free {

  type ConnectionIO[A]   = Connection.ConnectionIO[A]
  type Interpreter[F[_]] = Interpreter.Interpreter[F]

}
