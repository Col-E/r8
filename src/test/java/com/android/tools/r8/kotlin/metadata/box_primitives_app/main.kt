// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.box_primitives_app

import com.android.tools.r8.kotlin.metadata.box_primitives_lib.Test

fun main() {
  var test = Test()
  test.testBoolean.add(test.boolean)
  println(test.getFirstBoolean(test.testBoolean))
  test.testByte.add(test.byte)
  println(test.getFirstByte(test.testByte))
  test.testChar.add(test.char)
  println(test.getFirstChar(test.testChar))
  test.testDouble.add(test.double)
  println(test.getFirstDouble(test.testDouble))
  test.testFloat.add(test.float)
  println(test.getFirstFloat(test.testFloat))
  test.testInt.add(test.int)
  println(test.getFirstInt(test.testInt))
  test.testLong.add(test.long)
  println(test.getFirstLong(test.testLong))
  test.testShort.add(test.short)
  println(test.getFirstShort(test.testShort))
  test.testNumber.add(test.number)
  println(test.getFirstNumber(test.testNumber))
  test.functionWithUnit { println(it) }
  test.functionWithVoid { println(it); null }
}