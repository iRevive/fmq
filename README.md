# ƒMQ
[![Build Status](https://github.com/iRevive/fmq/workflows/CI/badge.svg)](https://github.com/iRevive/fmq/actions)
[![codecov](https://codecov.io/gh/iRevive/fmq/branch/master/graph/badge.svg)](https://codecov.io/gh/iRevive/fmq)
[![Maven Version](https://maven-badges.herokuapp.com/maven-central/io.github.irevive/fmq-core_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.irevive/fmq-core_2.13)

Functional bindings for ZeroMQ built-in on top of [cats-effect](https://github.com/typelevel/cats-effect) and [JeroMQ](https://github.com/zeromq/jeromq).

## Quick Start

To use ƒMQ in an existing SBT project with Scala 2.12 or a later version, add the following dependency to your `build.sbt`:
 
```scala
libraryDependencies += "io.github.irevive" %% "fmq-core" % "<version>"
```

## Usage Guide

Check the [official guide](https://irevive.github.io/fmq/) for additional information.

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
| Rep    | true        | true        | Bind / Connect      |
| Req    | true        | true        | Connect             |
| Router | true        | true        | Bind                |
| Dealer | true        | true        | Bind / Connect      |

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