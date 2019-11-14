package io.fmq.socket.internal

import io.fmq.domain.Port

private[socket] final case class ConnectedSocket[F[_]](socket: Socket[F], port: Port)
