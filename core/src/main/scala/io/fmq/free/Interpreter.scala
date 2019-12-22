package io.fmq.free

import cats.effect.Sync
import cats.~>
import io.fmq.free.ConnectionIO.ConnectionOp

object Interpreter {

  type Interpreter[F[_]] = ConnectionOp ~> F

  def interpret[F[_]](implicit F: Sync[F]): Interpreter[F] = new Interpreter[F] {
    override def apply[A](fa: ConnectionOp[A]): F[A] = fa match {
      case ConnectionOp.Delay(a) =>
        F.delay(a())

      case ConnectionOp.HandleErrorWith(fa, f) =>
        F.handleErrorWith(fa.foldMap(this))(f.andThen(_.foldMap(this)))

      case ConnectionOp.RaiseError(e) =>
        F.raiseError(e)

      case ConnectionOp.BracketCase(acquire, use, release) =>
        F.bracketCase(acquire.foldMap(this))(use.andThen(_.foldMap(this)))((a, e) => release(a, e).foldMap(this))
    }
  }

}
