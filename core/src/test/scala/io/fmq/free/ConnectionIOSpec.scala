package io.fmq.free

import cats.Eq
import cats.effect.laws.discipline.SyncTests
import cats.effect.laws.util.TestInstances
import cats.effect.{ContextShift, IO}
import cats.implicits._
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalatest.FunSuite
import org.scalatestplus.scalacheck.Checkers
import org.typelevel.discipline.Laws

import scala.concurrent.ExecutionContext

class ConnectionIOSpec extends FunSuite with Checkers with TestInstances {

  import ConnectionIOSpec.ConnectionIOScalaCheckInstances._

  checkAll("ConnectionIO", SyncTests[ConnectionIO].sync[Int, Int, Int])

  private def checkAll(name: String, ruleSet: Laws#RuleSet): Unit =
    for ((id, prop) <- ruleSet.all.properties if !ignoredLaws.contains(id))
      registerTest(s"$name.$id") {
        check(prop)
      }

  private lazy val ignoredLaws: Set[String] = Set(
    "sync.stack-safe on repeated attempts"
  )

}

object ConnectionIOSpec {

  object ConnectionIOScalaCheckInstances {
    implicit def cogenTask[A]: Cogen[ConnectionIO[A]] = Cogen[Unit].contramap(_ => ())

    implicit def ConnectionIOEq[A: Eq](implicit E: Eq[Throwable]): Eq[ConnectionIO[A]] =
      new Eq[ConnectionIO[A]] {
        private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
        private val interpret: Interpreter[IO]              = Interpreter.interpret[IO]

        def eqv(x: ConnectionIO[A], y: ConnectionIO[A]): Boolean =
          Eq[Either[Throwable, A]].eqv(
            x.foldMap(interpret).attempt.unsafeRunSync(),
            y.foldMap(interpret).attempt.unsafeRunSync()
          )
      }

    implicit def arbConnectionIO[A: Arbitrary: Cogen]: Arbitrary[ConnectionIO[A]] =
      Arbitrary(Gen.delay(genConnectionIO[A]))

    def genConnectionIO[A: Arbitrary: Cogen]: Gen[ConnectionIO[A]] =
      Gen.frequency(
        5 -> genDelay[A],
        1 -> genFail[A]
      )

    def genDelay[A: Arbitrary]: Gen[ConnectionIO[A]] =
      Arbitrary.arbitrary[A].map(Connection.delay(_))

    def genFail[A]: Gen[ConnectionIO[A]] =
      Arbitrary.arbitrary[Throwable].map(Connection.raiseError[A])

  }

}
