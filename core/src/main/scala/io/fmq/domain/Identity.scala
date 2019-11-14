package io.fmq.domain

sealed trait Identity

object Identity {

  @SuppressWarnings(Array("org.wartremover.warts.ArrayEquals"))
  final case class Fixed(value: Array[Byte]) extends Identity

  final case object Empty extends Identity

}
