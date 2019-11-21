package io

import cats.effect.IO
import cats.~>

package object fmq {

  // ConnectionIO does not have instance of cats.effect.Effect. Thus use this workaround
  type ToIO[F[_]] = F ~> IO

  object ToIO {
    def apply[F[_]](implicit ev: ToIO[F]): ToIO[F] = ev
  }

}
