# ƒMQ
[![Build Status](https://travis-ci.org/iRevive/fmq.svg?branch=master)](https://travis-ci.org/iRevive/fmq?branch=master)
[![codecov](https://codecov.io/gh/iRevive/fmq/branch/master/graph/badge.svg)](https://codecov.io/gh/iRevive/fmq)
[![Maven Version](https://maven-badges.herokuapp.com/maven-central/io.github.irevive/fmq-core_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.irevive/fmq-core_2.13)

Functional library for ZeroMQ built-in on top of [cats-effect](https://github.com/typelevel/cats-effect) and [JeroMQ](https://github.com/zeromq/jeromq).

## Quick Start

To use ƒMQ in an existing SBT project with Scala 2.12 or a later version, add the following dependencies to your `build.sbt` depending on your needs:
 
```scala
libraryDependencies += "io.github.irevive" %% "fmq-core" % "<version>"
```

## Usage Guide

Check the [official guide](https://irevive.github.io/fmq/) for additional information.

## Supported sockets

* Pub
* Sub
* Push
* Pull
