// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.box_primitives_app

import com.android.tools.r8.kotlin.metadata.box_primitives_lib.Test

fun trivialTypeAssertionsForProperties() {
  val test = Test()
  assert(test::boolean.returnType.classifier.toString().equals("class kotlin.Boolean"))
  assert(test::byte.returnType.classifier.toString().equals("class kotlin.Byte"))
  assert(test::char.returnType.classifier.toString().equals("class kotlin.Char"))
  assert(test::double.returnType.classifier.toString().equals("class kotlin.Double"))
  assert(test::float.returnType.classifier.toString().equals("class kotlin.Float"))
  assert(test::int.returnType.classifier.toString().equals("class kotlin.Int"))
  assert(test::long.returnType.classifier.toString().equals("class kotlin.Long"))
  assert(test::short.returnType.classifier.toString().equals("class kotlin.Short"))
  assert(test::number.returnType.classifier.toString().equals("class kotlin.Number"))
}

fun trivialTypeAssertionsForFunctions() {
  val test = Test()
  assert(test::getFirstBoolean.parameters.size == 1)
  assert(test::getFirstBoolean.parameters.get(0).name == "l")
  assert(test::getFirstBoolean
           .parameters.get(0).type.classifier.toString() == "class kotlin.collections.List")
  assert(test::getFirstBoolean.parameters.get(0).type.arguments.size == 1)
  assert(test::getFirstBoolean
           .parameters.get(0).type.arguments.get(0).type!!.classifier.toString().equals(
                test::boolean.returnType.classifier.toString()))
}

fun runReflective() {
  val test = Test()
  // Test boxed types
  test::boolean.set(false)
  test::testBoolean.get().add(test::boolean.get())
  println(test::getFirstBoolean.call(test::testBoolean.get()))
  test::byte.set(0)
  test::testByte.get().add(test::byte.get())
  println(test::getFirstByte.call(test::testByte.get()))
  test::char.set('a')
  test::testChar.get().add(test::char.get())
  println(test::getFirstChar.call(test::testChar.get()))
  test::double.set(0.042)
  test::testDouble.get().add(test::double.get())
  println(test::getFirstDouble.call(test::testDouble.get()))
  test::float.set(0.42F)
  test::testFloat.get().add(test::float.get())
  println(test::getFirstFloat.call(test::testFloat.get()))
  test::int.set(42)
  test::testInt.get().add(test::int.get())
  println(test::getFirstInt.call(test::testInt.get()))
  test::long.set(442)
  test::testLong.get().add(test::long.get())
  println(test::getFirstLong.call(test::testLong.get()))
  test::short.set(1)
  test::testShort.get().add(test::short.get())
  println(test::getFirstShort.call(test::testShort.get()))
  test::number.set(2)
  test::testNumber.get().add(test::number.get())
  println(test::getFirstNumber.call(test::testNumber.get()))
  test::functionWithUnit.call({ i: Int -> println(i) })
  test::functionWithVoid.call({ i: Int -> println(i); null })
}

fun main() {
  trivialTypeAssertionsForProperties()
  trivialTypeAssertionsForFunctions()
  runReflective()
}