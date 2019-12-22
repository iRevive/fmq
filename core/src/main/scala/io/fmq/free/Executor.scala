package io.fmq.free

import cats.effect.{Blocker, ContextShift, Sync}
import cats.~>
import io.fmq.free.ConnectionIO.ConnectionIO
import io.fmq.free.Interpreter.Interpreter

sealed abstract class Executor[F[_]] {

  def interpret: Interpreter[F]

  def executeK(blocker: Blocker)(implicit F: Sync[F], CS: ContextShift[F]): ConnectionIO ~> F =
    λ[ConnectionIO ~> F] { fa =>
      blocker.blockOn(fa.foldMap(interpret))
    }

}

object Executor {

  def apply[F[_]: Sync: ContextShift]: Executor[F] = new Executor[F] {
    override val interpret: Interpreter[F] = Interpreter.interpret[F]
  }

}
