// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.typealias_app

import com.android.tools.r8.kotlin.metadata.typealias_lib.API
import com.android.tools.r8.kotlin.metadata.typealias_lib.AlphaNaming
import com.android.tools.r8.kotlin.metadata.typealias_lib.Arr1D
import com.android.tools.r8.kotlin.metadata.typealias_lib.Arr2D
import com.android.tools.r8.kotlin.metadata.typealias_lib.Arr2DTester
import com.android.tools.r8.kotlin.metadata.typealias_lib.CWithConstructor
import com.android.tools.r8.kotlin.metadata.typealias_lib.CWithConstructorTester
import com.android.tools.r8.kotlin.metadata.typealias_lib.ClassWithCompanionC
import com.android.tools.r8.kotlin.metadata.typealias_lib.FunctionTester
import com.android.tools.r8.kotlin.metadata.typealias_lib.Impl
import com.android.tools.r8.kotlin.metadata.typealias_lib.IntSet
import com.android.tools.r8.kotlin.metadata.typealias_lib.InterfaceTester
import com.android.tools.r8.kotlin.metadata.typealias_lib.MyAdvancedMap
import com.android.tools.r8.kotlin.metadata.typealias_lib.MyI
import com.android.tools.r8.kotlin.metadata.typealias_lib.MyMapToSetOfInt
import com.android.tools.r8.kotlin.metadata.typealias_lib.OuterNested
import com.android.tools.r8.kotlin.metadata.typealias_lib.OuterNestedInner
import com.android.tools.r8.kotlin.metadata.typealias_lib.OuterTester
import com.android.tools.r8.kotlin.metadata.typealias_lib.SimpleClassTester
import com.android.tools.r8.kotlin.metadata.typealias_lib.StillCWithConstructor
import com.android.tools.r8.kotlin.metadata.typealias_lib.SubTypeOfAlias
import com.android.tools.r8.kotlin.metadata.typealias_lib.UnderlyingTypeTester
import com.android.tools.r8.kotlin.metadata.typealias_lib.UnusedTypeArgument
import com.android.tools.r8.kotlin.metadata.typealias_lib.VerticalClassMergingTester
import com.android.tools.r8.kotlin.metadata.typealias_lib.seq

class ProgramClass : Impl() {
  override fun foo(): API {
    super.foo()
    println("Program::foo")
    return this
  }
}

fun testUnusedArgument() {
  val u = UnusedTypeArgument<Int>();
}

fun testSimpleClass() {
  val simple = object : AlphaNaming() { }
  println(SimpleClassTester.f(SimpleClassTester.g(simple)).y)
}

fun testArr2D() {
  val arr1d : Arr1D<Int> = Arr1D(42);
  val arr2d : Arr2D<Int> = Arr2D(arr1d);
  println(Arr2DTester.f(Arr2DTester.g(arr2d)).x.x);
}

fun testInterface() {
  val myInstance = object : MyI {
    override fun f() {
      println("42");
    }
  }
  InterfaceTester.f(myInstance).f()

  val map : MyMapToSetOfInt<Int> = HashMap();
  val set : IntSet = mutableSetOf(42);
  map.put(1, set);
  println(InterfaceTester.i(InterfaceTester.h(map))[1]?.iterator()?.next() ?: "");
}

fun testFunctionTypes() {
  FunctionTester.f(FunctionTester.g({ i : Int, a : Any ->
    println(i)
    println(a)
  }))(42, "42")
  FunctionTester.h(FunctionTester.i({ b ->
    println(b)
    false
  }))(true)
}

fun testNestedClasses() {
  val nested = OuterNested(42);
  val myInner : OuterNestedInner = nested.Inner(1)
  println(OuterTester.f(OuterTester.g(nested)).y)
  println(OuterTester.h(OuterTester.i(myInner)).x)
}

fun testCompanion() {
  println(ClassWithCompanionC.fooOnCompanion);
}

fun testConstructor() {
  println(CWithConstructorTester.f(CWithConstructorTester.g(CWithConstructor(42))).x);
}

fun testUnderlyingType() {
  val cWithConstructor = StillCWithConstructor(42)
  println(UnderlyingTypeTester.f(UnderlyingTypeTester.g(cWithConstructor)).x)
  val advancedMap : MyAdvancedMap = HashMap();
  val nested = OuterNested(42);
  val myInner : OuterNestedInner = nested.Inner(1)
  advancedMap.put(nested, myInner);
  val sameMap = UnderlyingTypeTester.h(UnderlyingTypeTester.i(advancedMap))
  println(sameMap.get(nested)?.x)
}

fun testVerticalClassMerging() {
  val apiImpl = VerticalClassMergingTester.produce()
  VerticalClassMergingTester.passThrough(apiImpl).foo()
}

fun testSuperType() {
  println(SubTypeOfAlias::class.supertypes[0].classifier)
}

fun main() {
  val instance = ProgramClass()
  val l = seq(instance)
  for (api in l) {
    println(api == api.foo())
    println(api.hey())
  }
  testUnusedArgument()
  testSimpleClass()
  testArr2D()
  testInterface()
  testFunctionTypes()
  testNestedClasses()
  testCompanion()
  testConstructor()
  testUnderlyingType()
  testVerticalClassMerging()
  testSuperType()
}
