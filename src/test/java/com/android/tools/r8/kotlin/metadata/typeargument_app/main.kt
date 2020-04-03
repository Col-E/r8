// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.typeargument_app

import com.android.tools.r8.kotlin.metadata.typeargument_lib.CoVariant
import com.android.tools.r8.kotlin.metadata.typeargument_lib.ContraVariant
import com.android.tools.r8.kotlin.metadata.typeargument_lib.Invariant
import com.android.tools.r8.kotlin.metadata.typeargument_lib.PlainBox
import com.android.tools.r8.kotlin.metadata.typeargument_lib.SomeClass
import com.android.tools.r8.kotlin.metadata.typeargument_lib.asList
import com.android.tools.r8.kotlin.metadata.typeargument_lib.asListWithVarargs
import com.android.tools.r8.kotlin.metadata.typeargument_lib.asListWithVarargs2
import com.android.tools.r8.kotlin.metadata.typeargument_lib.asObfuscatedClass
import com.android.tools.r8.kotlin.metadata.typeargument_lib.asStar
import com.android.tools.r8.kotlin.metadata.typeargument_lib.unBoxAndBox
import com.android.tools.r8.kotlin.metadata.typeargument_lib.unboxAndPutInBox
import com.android.tools.r8.kotlin.metadata.typeargument_lib.update

class SomeSubClass(val x : Int) : SomeClass(), Comparable<SomeClass> {

  override fun compareTo(other: SomeClass): Int {
    if (other !is SomeSubClass) {
      return -1;
    }
    return x.compareTo(other.x);
  }
}

fun testInvariant() {
  val subtype1 = SomeSubClass(42)
  val subtype2 = SomeSubClass(1)
  val inv = Invariant<SomeSubClass, String>("Hello World!")
  println(inv.classGenerics(subtype1).x)
  println(inv.funGenerics(subtype2).x)
  println(inv.funGenericsWithUpperBound(subtype1).x)
  println(inv.funGenericsWithUpperBounds(subtype1, subtype2).x)
  println(inv.mixedGenerics(subtype2).x)
}

fun testCoVariant() {
 println(CoVariant(42).classGenerics().t)
}

fun testContraVariant() {
  ContraVariant<Number>().classGenerics(42)
}

fun testExtension() {
  println(CoVariant(42).unBoxAndBox().t)
  println(CoVariant(1).update(42).t)
  println(CoVariant(1).unboxAndPutInBox(CoVariant(42)).t)
  println(CoVariant(42).asList().t.get(0))
  val asList = CoVariant(42).asListWithVarargs(1, 2)
  println(asList.t.get(0))
  println(asList.t.get(1))
  println(CoVariant(9).asListWithVarargs2(CoVariant(3)).t.get(0))
  // Peeking into the result will result in an error since the underlying type has been renamed.
  CoVariant(7).asObfuscatedClass()
  println(CoVariant(42).asStar().t)
}

fun testPlainBox() {
  var plainBox = PlainBox(42)
  println(plainBox.plainBox)
  plainBox.plainBox = 7
  println(plainBox.plainBox)
}

fun main() {
  testInvariant()
  testCoVariant()
  testContraVariant();
  testExtension()
  testPlainBox()
}