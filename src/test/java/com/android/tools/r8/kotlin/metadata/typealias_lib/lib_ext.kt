// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
@file:Suppress("UNCHECKED_CAST")

package com.android.tools.r8.kotlin.metadata.typealias_lib

// Unused type aliases

open class LibraryClass { }

// KmTypeAlias {
//   name: Unused
//   underlyingType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/LibraryClass)
//   }
//   expandedType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/LibraryClass)
//   }
// },
typealias Unused = LibraryClass

// Will give a warning, but is is not an error to have an unused argument.
// KmTypeAlias {
//   name: UnusedTypeArgument
//   typeParameters: T
//   underlyingType {
//     classifier: TypeAlias(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Unused)
//   }
//   expandedType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/LibraryClass)
//   }
// },
typealias UnusedTypeArgument<T> = Unused

// Expansion to simple class

open class SimpleClass {
  val y : Int = 42;
}

// KmTypeAlias {
//   name: AlphaNaming
//   underlyingType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/SimpleClass)
//   }
//   expandedType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/SimpleClass)
//   }
// },
typealias AlphaNaming = SimpleClass

class SimpleClassTester {

  companion object {
    fun f(a : Any) : AlphaNaming {
      return a as AlphaNaming;
    }

    fun g(a : AlphaNaming) : Any {
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

// KmTypeAlias {
//   name: ApiAlias
//   underlyingType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Api)
//   }
//   expandedType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Api)
//   }
// },
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

// KmTypeAlias {
//   name: Arr1D
//   typeParameters: K
//   underlyingType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Arr)
//     arguments: TypeParameter(id=0)
//   }
//   expandedType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Arr)
//     arguments: TypeParameter(id=0)
//   }
// },
typealias Arr1D<K> = Arr<K>

// KmTypeAlias {
//   name: Arr2D
//   typeParameters: K
//   underlyingType {
//     classifier: TypeAlias(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Arr1D)
//     arguments: TypeAlias(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Arr1D)
//   }
//   expandedType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Arr)
//     arguments: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Arr)
//   }
// },
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

// KmTypeAlias {
//   name: MyI
//   underlyingType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/I)
//     arguments: Class(name=kotlin/Int)
//   }
//   expandedType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/I)
//     arguments: Class(name=kotlin/Int)
//   }
// },
typealias MyI = I<Int>

// KmTypeAlias {
//   name: IntSet
//   underlyingType {
//     classifier: Class(name=kotlin/collections/Set)
//     arguments: Class(name=kotlin/Int)
//   }
//   expandedType {
//     classifier: Class(name=kotlin/collections/Set)
//     arguments: Class(name=kotlin/Int)
//   }
// },
typealias IntSet = Set<Int>

// KmTypeAlias {
//   name: MyMapToSetOfInt
//   typeParameters: K
//   underlyingType {
//     classifier: Class(name=kotlin/collections/MutableMap)
//     arguments: TypeParameter(id=0),TypeAlias(name=com/android/tools/r8/kotlin/metadata/typealias_lib/IntSet)
//   }
//   expandedType {
//     classifier: Class(name=kotlin/collections/MutableMap)
//     arguments: TypeParameter(id=0),Class(name=kotlin/collections/Set)
//   }
// },
typealias MyMapToSetOfInt<K> = MutableMap<K, IntSet>

class InterfaceTester {

  companion object {

    fun f(i : Any) : MyI {
      return i as MyI
    }

    fun g(myI : MyI) : Any {
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

// KmTypeAlias {
//   name: MyHandler
//   underlyingType {
//     classifier: Class(name=kotlin/Function2)
//     arguments: Class(name=kotlin/Int),Class(name=kotlin/Any),Class(name=kotlin/Unit)
//   }
//   expandedType {
//     classifier: Class(name=kotlin/Function2)
//     arguments: Class(name=kotlin/Int),Class(name=kotlin/Any),Class(name=kotlin/Unit)
//   }
// },
typealias MyHandler = (Int, Any) -> Unit

// KmTypeAlias {
//   name: MyGenericPredicate
//   typeParameters: T
//   underlyingType {
//     classifier: Class(name=kotlin/Function1)
//     arguments: TypeParameter(id=0),Class(name=kotlin/Boolean)
//   }
//   expandedType {
//     classifier: Class(name=kotlin/Function1)
//     arguments: TypeParameter(id=0),Class(name=kotlin/Boolean)
//   }
// },
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

// KmTypeAlias {
//   name: OuterNested
//   underlyingType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Outer.Nested)
//   }
//   expandedType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Outer.Nested)
//   }
// },
typealias OuterNested = Outer.Nested

// KmTypeAlias {
//   name: OuterNestedInner
//   underlyingType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Outer.Nested.Inner)
//   }
//   expandedType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Outer.Nested.Inner)
//   }
// },
typealias OuterNestedInner = Outer.Nested.Inner

class OuterTester {

  companion object {

    fun f(a : Any) : OuterNested {
      return a as OuterNested;
    }

    fun g(a : OuterNested) : Any {
      return a;
    }

    fun h(a : Any) : OuterNestedInner {
      return a as OuterNestedInner;
    }

    fun i(a : OuterNestedInner) : Any {
      return a;
    }
  }
}

// Expansion to companion class

class ClassWithCompanion {

  companion object {
    val fooOnCompanion: String
      get() = "ClassWithCompanion::fooOnCompanion"
  }
}

// KmTypeAlias {
//   name: ClassWithCompanionC
//   underlyingType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/ClassWithCompanion.Companion)
//   }
//   expandedType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/ClassWithCompanion.Companion)
//   }
// },
typealias ClassWithCompanionC = ClassWithCompanion.Companion

// Expansion to constructor

class C(val x : Int) {

  private constructor() : this(0)
}

// KmTypeAlias {
//   name: CWithConstructor
//   underlyingType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/C)
//   }
//   expandedType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/C)
//   }
// },
typealias CWithConstructor = C

class CWithConstructorTester {

  companion object {

    fun f(a : Any) : CWithConstructor {
      return a as CWithConstructor;
    }

    fun g(a : CWithConstructor) : Any {
      return a;
    }
  }
}

// Underlying type having type alias

// KmTypeAlias {
//   name: StillCWithConstructor
//   underlyingType {
//     classifier: TypeAlias(name=com/android/tools/r8/kotlin/metadata/typealias_lib/CWithConstructor)
//   }
//   expandedType {
//     classifier: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/C)
//   }
// },
typealias StillCWithConstructor = CWithConstructor

// KmTypeAlias {
//   name: MyAdvancedMap
//   underlyingType {
//     classifier: Class(name=kotlin/collections/MutableMap)
//     arguments: TypeAlias(name=com/android/tools/r8/kotlin/metadata/typealias_lib/OuterNested),TypeAlias(name=com/android/tools/r8/kotlin/metadata/typealias_lib/OuterNestedInner)
//   }
//   expandedType {
//     classifier: Class(name=kotlin/collections/MutableMap)
//     arguments: Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Outer.Nested),Class(name=com/android/tools/r8/kotlin/metadata/typealias_lib/Outer.Nested.Inner)
//   }
// },
typealias MyAdvancedMap = MutableMap<OuterNested, OuterNestedInner>

class UnderlyingTypeTester {

  companion object {

    fun f(a : StillCWithConstructor) : CWithConstructor {
      return a;
    }

    fun g(a : CWithConstructor) : StillCWithConstructor {
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

open class Super

typealias TypeAliasForSuper = Super

class SubTypeOfAlias : TypeAliasForSuper()
