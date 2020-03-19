// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.typeargument_lib

open class SomeClass

class Invariant<T> {

  fun classGenerics(t : T) : T {
    return t
  }

  fun <R> funGenerics(r : R) : R {
    return r
  }

  fun <R : SomeClass> funGenericsWithUpperBound(r : R) : R {
    return r;
  }

  fun <R> funGenericsWithUpperBounds(r1 : R, r2 : R) : R
    where R : SomeClass,
          R : Comparable<SomeClass> {
    return when {
      r1 > r2 -> {
        r1;
      }
      else -> {
        r2;
      }
    }
  }

  fun <R : T> mixedGenerics(r : R) : T {
    return r;
  }
}

class CoVariant<out T>(val t : T) {

  fun classGenerics() : CoVariant<T> {
    return CoVariant(t);
  }
}

class ContraVariant<in T> {

  fun classGenerics(t : T) {
    println(t)
  }
}


fun <T> CoVariant<T>.unBoxAndBox() : CoVariant<T> {
  return CoVariant(this.t)
}

fun <T, R> CoVariant<R>.update(t : T) : CoVariant<T> {
  println(this.t)
  return CoVariant(t)
}
