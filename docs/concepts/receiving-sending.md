---
id: receiving-sending
title: Receiving and Sending
---

The library provides high-level and low-level APIs for receiving and sending.

## Receiving

The sockets that can receive messages extends from `io.fmq.socket.ConsumerSocket`.  

The `io.fmq.frame.FrameDecoder` is a typeclass that provides a conversion from `Array[Byte]` to `A`.  
There are two predefined instances: `FrameDecoder[Array[Byte]]` and `FrameDecoder[String]`.

### Low-level API

There are several low-level methods:

1) `def receive[A: FrameDecoder]: F[A]`  
Receives a single message. **Blocks indefinitely** until a message arrives. 
You should call manually the `hasReceiveMore` method to verify that message is multipart or not. 
 
2) `def receiveNoWait[A: FrameDecoder]: F[Option[A]]`  
Tries to receive a single message immediately (without blocking). If message is not available returns `None`.

3) `def hasReceiveMore: F[Boolean]`  
Returns true if message is multipart.

### High-level API

The `def receiveFrame[A: FrameDecoder]: F[Frame[A]]` **blocks indefinitely** until a message arrives.   
Consumes a multipart message automatically. The method returns `Frame.Multipart` if message is multipart, otherwise returns `Frame.Single`.


## Sending

The sockets that can send messages extends from `io.fmq.socket.ProducerSocket`.  

The `io.fmq.frame.FrameEncoder` is a typeclass that provides a conversion from `A` to `Array[Byte]`.  
There are two predefined instances: `FrameEncoder[Array[Byte]]` and `FrameEncoder[String]`.

### Low-level API

There are several low-level methods:

1) `def send[A: FrameEncoder](value: A): F[Unit]`  
Queues a message to be sent. The data is either a single-part message by itself, or the last part of a multi-part message.
 
2) `def sendMore[A: FrameEncoder](value: A): F[Unit]`  
Queues a multi-part message to be sent.

### High-level API

The `def sendFrame[A: FrameEncoder](frame: Frame[A]): F[Unit]` queues a message to be sent.
Sends a multipart message automatically.  

