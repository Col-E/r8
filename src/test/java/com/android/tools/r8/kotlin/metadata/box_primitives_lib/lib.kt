// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.box_primitives_lib

class Test {

  val testReadOnly : List<Boolean> = listOf()

  var boolean : Boolean = false
  var testBoolean : MutableList<Boolean> = mutableListOf()
  var byte : Byte = 0
  var testByte : MutableList<Byte> = mutableListOf()
  var char : Char = 'a'
  var testChar : MutableList<Char> = mutableListOf()
  var double : Double = 0.042
  var testDouble : MutableList<Double> = mutableListOf()
  var float : Float = 0.42F
  var testFloat : MutableList<Float> = mutableListOf()
  var int : Int = 42
  var testInt : MutableList<Int> = mutableListOf()
  var long : Long = 442
  var testLong : MutableList<Long> = mutableListOf()
  var short : Short = 1
  var testShort : MutableList<Short> = mutableListOf()
  var number : Number = 2
  var testNumber : MutableList<Number> = mutableListOf()

  fun getFirstBoolean(l : List<Boolean>) : Boolean = l.get(0)
  fun getFirstByte(l : List<Byte>) : Byte = l.get(0)
  fun getFirstChar(l : List<Char>) : Char = l.get(0)
  fun getFirstDouble(l : List<Double>) : Double = l.get(0)
  fun getFirstFloat(l : List<Float>) : Float = l.get(0)
  fun getFirstInt(l : List<Int>) : Int = l.get(0)
  fun getFirstLong(l : List<Long>) : Long = l.get(0)
  fun getFirstShort(l : List<Short>) : Short = l.get(0)
  fun getFirstNumber(l : List<Number>) : Number = l.get(0)
  fun functionWithUnit(consumer : (Int) -> Unit) = consumer(42)
  fun functionWithVoid(consumer : (Int) -> Void?) = consumer(42)
}
