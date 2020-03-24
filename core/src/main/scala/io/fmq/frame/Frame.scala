package io.fmq.frame

import cats.data.NonEmptyList

sealed trait Frame[A] {
  def parts: NonEmptyList[A]
}

object Frame {

  final case class Single[A](value: A) extends Frame[A] {
    override def parts: NonEmptyList[A] = NonEmptyList.one(value)
  }

  final case class Multipart[A](header: A, body: NonEmptyList[A]) extends Frame[A] {
    override def parts: NonEmptyList[A] = body.prepend(header)
  }

  object Multipart {

    @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
    def apply[A](header: A, next: A, rest: A*): Multipart[A] =
      Multipart(header, NonEmptyList(next, rest.toList))
  }

}
