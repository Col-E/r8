// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.typealias_lib

// Unused type aliases

open class LibraryClass { }

//  KmAlias {
//    name: Unused
//    underlyingType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/LibraryClass;
//    expandedType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/LibraryClass;
//  },
typealias Unused = LibraryClass

// Will give a warning, but is is not an error to have an unused argument.
//  KmAlias {
//    name: UnusedTypeArgument
//    underlyingType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/Unused;
//    expandedType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/LibraryClass;
//  },
typealias UnusedTypeArgument<T> = Unused

// Expansion to simple class

open class SimpleClass {
  val y : Int = 42;
}

//  KmAlias {
//    name: AlphaNaming
//    underlyingType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/SimpleClass;
//    expandedType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/SimpleClass;
//  },
typealias AlphaNaming = SimpleClass

class SimpleClassTester {

  companion object {
    fun f(a : SimpleClass) : AlphaNaming {
      return a;
    }

    fun g(a : AlphaNaming) : SimpleClass {
      return a;
    }
  }
}

// Vertical class merging

interface Api {

  fun foo();
}

class ApiImpl : Api {
  override fun foo() {
    println("Hello World!")
  }
}

//  KmAlias {
//    name: ApiAlias
//    underlyingType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/Api;
//    expandedType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/Api;
//  },
typealias ApiAlias = Api

class VerticalClassMergingTester {

  companion object {

    fun produce(): ApiAlias {
      return ApiImpl()
    }

    fun passThrough(a : ApiAlias) : ApiAlias {
      return a;
    }
  }
}

// Multiple expansions

class Arr<K>(val x : K)

//  KmAlias {
//    name: Arr1D
//    underlyingType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/Arr;
//    expandedType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/Arr;
//    },
typealias Arr1D<K> = Arr<K>

//  KmAlias {
//    name: Arr2D
//    underlyingType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/Arr;
//    expandedType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/Arr;
//  },
typealias Arr2D<K> = Arr1D<Arr1D<K>>

class Arr2DTester {

  companion object {

    fun <K> f(a : Arr1D<Arr1D<K>>) : Arr2D<K> {
      return a;
    }

    fun <K> g(a : Arr2D<K>) : Arr1D<Arr1D<K>> {
      return a;
    }

  }
}

// Expansion to interfaces

interface I<T : Any> {
  fun f()
}

//  KmAlias {
//    name: MyI
//    underlyingType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/I;
//    expandedType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/I;
//  },
typealias MyI = I<Int>

//  KmAlias {
//    name: IntSet
//    underlyingType: Lkotlin/collections/Set;
//    expandedType: Lkotlin/collections/Set;
//  },
typealias IntSet = Set<Int>

//  KmAlias {
//    name: MyMapToSetOfInt
//    underlyingType: Lkotlin/collections/Map;
//    expandedType: Lkotlin/collections/Map;
//  },
typealias MyMapToSetOfInt<K> = MutableMap<K, IntSet>

class InterfaceTester {

  companion object {

    fun f(i : I<Int>) : MyI {
      return i;
    }

    fun g(myI : MyI) : I<Int> {
      return myI;
    }

    fun h(k : MyMapToSetOfInt<Int>) : MutableMap<Int, IntSet> {
      return k;
    }

    fun i(myMap : MutableMap<Int, IntSet>) : MyMapToSetOfInt<Int> {
      return myMap;
    }
  }
}

// Expansion to function types

//  KmAlias {
//    name: MyHandler
//    underlyingType: Lkotlin/Function2;
//    expandedType: Lkotlin/Function2;
//  },
typealias MyHandler = (Int, Any) -> Unit

//  KmAlias {
//    name: MyGenericPredicate
//    underlyingType: Lkotlin/Function1;
//    expandedType: Lkotlin/Function1;
//  },
typealias MyGenericPredicate<T> = (T) -> Boolean

class FunctionTester {

  companion object {

    fun f(a : (Int, Any) -> Unit) : MyHandler {
      return a;
    }

    fun g(a : MyHandler) : (Int, Any) -> Unit {
      return a;
    }

    fun h(a : (Boolean) -> Boolean) : MyGenericPredicate<Boolean> {
      return a;
    }

    fun i(a : MyGenericPredicate<Boolean>) : (Boolean) -> Boolean {
      return a;
    }
  }
}

// Expansion to nested classes

class Outer {
  class Nested(val y : Int) {
    inner class Inner(val x : Int) {

    }
  }
}

//  KmAlias {
//    name: OuterNested
//    underlyingType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/Outer$Nested;
//    expandedType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/Outer$Nested;
//  },
typealias OuterNested = Outer.Nested

//  KmAlias {
//    name: OuterNestedInner
//    underlyingType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/Outer$Nested$Inner;
//    expandedType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/Outer$Nested$Inner;
//  },
typealias OuterNestedInner = Outer.Nested.Inner

class OuterTester {

  companion object {

    fun f(a : Outer.Nested.Inner) : OuterNestedInner {
      return a;
    }

    fun g(a : OuterNested) : Outer.Nested {
      return a;
    }

  }
}

// Expansion to companion class

class ClassWithCompanion {

  companion object {
    val foo: String
      get() = "A.Companion::foo"
  }
}

//  KmAlias {
//    name: ClassWithCompanionC
//    underlyingType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/ClassWithCompanion$Companion;
//    expandedType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/ClassWithCompanion$Companion;
//  },
typealias ClassWithCompanionC = ClassWithCompanion.Companion

// Expansion to constructor

class C(val x : Int) {

  private constructor() : this(0)
}

//  KmAlias {
//    name: CWithConstructor
//    underlyingType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/C;
//    expandedType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/C;
//  },
typealias CWithConstructor = C

class CWithConstructorTester {

  companion object {

    fun f(a : C) : CWithConstructor {
      return a;
    }

    fun g(a : CWithConstructor) : C {
      return a;
    }
  }
}

// Underlying type having type alias

//  KmAlias {
//    name: StillCWithConstructor
//    underlyingType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/CWithConstructor;
//    expandedType: Lcom/android/tools/r8/kotlin/metadata/typealias_lib/C;
//  },
typealias StillCWithConstructor = CWithConstructor

//  KmAlias {
//    name: MyAdvancedMap
//    underlyingType: Lkotlin/collections/Map;
//    expandedType: Lkotlin/collections/Map;
//  },
typealias MyAdvancedMap = MutableMap<OuterNested, OuterNestedInner>

class UnderlyingTypeTest {

  companion object {

    fun f(a : StillCWithConstructor) : CWithConstructor {
      return a;
    }

    fun g(a : C) : StillCWithConstructor {
      return a;
    }

    fun h(a : MutableMap<OuterNested, OuterNestedInner>) : MyAdvancedMap {
      return a;
    }

    fun i(a : MyAdvancedMap) : MutableMap<OuterNested, OuterNestedInner> {
      return a;
    }

  }
}
