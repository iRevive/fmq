package io.fmq.socket.internal

private[socket] trait ReceiveApi[F[_]] {

  protected def socket: Socket[F]

  def recv: F[Array[Byte]]       = socket.recv
  def recvString: F[String]      = socket.recvString
  def hasReceiveMore: F[Boolean] = socket.hasReceiveMore

}
