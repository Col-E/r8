// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package enumswitch

enum class KDirection {
  North,
  South,
  East,
  West
}

fun direction1(direction: KDirection) = when (direction) {
  KDirection.North -> "N"
  KDirection.South -> "S"
  KDirection.East -> "E"
  KDirection.West -> "W"
}

// Different declaration order than direction1 or direction2
fun direction2(direction: KDirection) = when (direction) {
  KDirection.East -> "E"
  KDirection.North -> "N"
  KDirection.West -> "W"
  KDirection.South -> "S"
}

fun main(args: Array<String>) {
  println(direction1(KDirection.North))
  println(direction1(KDirection.South))
  println(direction1(KDirection.East))
  println(direction1(KDirection.West))
  println(direction2(KDirection.North))
  println(direction2(KDirection.South))
  println(direction2(KDirection.East))
  println(direction2(KDirection.West))
}
