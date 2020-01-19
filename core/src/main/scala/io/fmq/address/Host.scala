package io.fmq.address

sealed abstract class Host(val value: String)

object Host {
  final case object Wildcard              extends Host("*")
  final case class Fixed(address: String) extends Host(address)
}
