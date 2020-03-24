---
id: index
title: Getting started
---

To use ƒMQ in an existing SBT project with Scala 2.12 or a later version, add the following dependency to your `build.sbt`:
 
```scala
libraryDependencies += "io.github.irevive" %% "fmq-core" % "@VERSION@"
libraryDependencies += "io.github.irevive" %% "fmq-extras" % "@VERSION@"
```

## Supported protocols

* TCP
* InProc

## Sockets matrix

| Socket | Can publish | Can receive | Connectivity method |
|--------|-------------|-------------|---------------------|
| Pub    | true        | false       | Bind                |
| Sub    | false       | true        | Connect             |
| XPub   | true        | true        | Bind                |
| XSub   | true        | true        | Connect             |
| Pull   | false       | true        | Bind                |
| Push   | true        | false       | Connect             |
| Rep    | true        | true        | Bind                |
| Req    | true        | true        | Connect             |

## Benchmarks

ƒMQ provides a great message throughput comparing to the native implementation.

[ƒMQ](https://github.com/iRevive/fmq/blob/master/bench/src/main/scala/io/fmq/SocketBenchmark.scala) msgs/s:

| Message size (bytes) | Throughput |
|----------------------|------------|
| 128                  | 1960737    |
| 256                  | 1511724    |
| 512                  | 862353     |
| 1024                 | 498450     |

[ØMQ](http://wiki.zeromq.org/results:ib-tests-v206) msgs/s: 

| Message size (bytes) | Throughput |
|----------------------|------------|
| 128                  | 3885802    |
| 256                  | 2689235    |
| 512                  | 1598083    |
| 1024                 | 867274     |

Hardware:  
MacBook Pro (15-inch, 2016)  
2,6 GHz Quad-Core Intel Core i7  
16 GB 2133 MHz LPDDR3  