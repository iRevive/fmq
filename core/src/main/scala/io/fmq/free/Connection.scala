package io.fmq.free

import cats.effect.{ExitCase, Sync}
import cats.free.Free

object Connection {

  sealed trait ConnectionOp[A]

  object ConnectionOp {

    final case class Delay[A](a: () => A) extends ConnectionOp[A]

    final case class HandleErrorWith[A](fa: ConnectionIO[A], f: Throwable => ConnectionIO[A]) extends ConnectionOp[A]

    final case class RaiseError[A](e: Throwable) extends ConnectionOp[A]

    final case class BracketCase[A, B](
        acquire: ConnectionIO[A],
        use: A => ConnectionIO[B],
        release: (A, ExitCase[Throwable]) => ConnectionIO[Unit]
    ) extends ConnectionOp[B]

  }

  type ConnectionIO[A] = Free[ConnectionOp, A]

  def delay[A](a: => A): ConnectionIO[A] = Free.liftF(ConnectionOp.Delay(() => a))

  def handleErrorWith[A](fa: ConnectionIO[A], f: Throwable => ConnectionIO[A]): ConnectionIO[A] =
    Free.liftF[ConnectionOp, A](ConnectionOp.HandleErrorWith(fa, f))

  def raiseError[A](err: Throwable): ConnectionIO[A] =
    Free.liftF[ConnectionOp, A](ConnectionOp.RaiseError(err))

  def bracketCase[A, B](
      acquire: ConnectionIO[A]
  )(use: A => ConnectionIO[B])(release: (A, ExitCase[Throwable]) => ConnectionIO[Unit]): ConnectionIO[B] =
    Free.liftF[ConnectionOp, B](ConnectionOp.BracketCase(acquire, use, release))

  implicit val connectionIOSync: Sync[ConnectionIO] =
    new Sync[ConnectionIO] {
      private val monad = Free.catsFreeMonadForFree[ConnectionOp]

      def bracketCase[A, B](
          acquire: ConnectionIO[A]
      )(use: A => ConnectionIO[B])(release: (A, ExitCase[Throwable]) => ConnectionIO[Unit]): ConnectionIO[B] =
        Connection.bracketCase(acquire)(use)(release)

      def pure[A](x: A): ConnectionIO[A] =
        monad.pure(x)

      def handleErrorWith[A](fa: ConnectionIO[A])(f: Throwable => ConnectionIO[A]): ConnectionIO[A] =
        Connection.handleErrorWith(fa, f)

      def raiseError[A](e: Throwable): ConnectionIO[A] =
        Connection.raiseError(e)

      def flatMap[A, B](fa: ConnectionIO[A])(f: A => ConnectionIO[B]): ConnectionIO[B] =
        monad.flatMap(fa)(f)

      def tailRecM[A, B](a: A)(f: A => ConnectionIO[Either[A, B]]): ConnectionIO[B] =
        monad.tailRecM(a)(f)

      def suspend[A](thunk: => ConnectionIO[A]): ConnectionIO[A] =
        monad.flatten(Connection.delay(thunk))
    }

}
