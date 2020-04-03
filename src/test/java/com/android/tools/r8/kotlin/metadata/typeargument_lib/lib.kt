// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.typeargument_lib

open class SomeClass

class PlainBox<T>(var plainBox : T)

class ClassThatWillBeObfuscated(val x : Int)

class Invariant<T, C> {

  constructor(someValue : C) {
    println(someValue)
  }

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

fun <T> CoVariant<T>.unboxAndPutInBox(box : CoVariant<T>) : CoVariant<T> {
  println(this.t)
  println(box.t)
  return CoVariant(box.t)
}

inline fun <reified T> CoVariant<T>.asList() : CoVariant<Array<T>> {
  println(this.t)
  return CoVariant(arrayOf(this.t))
}

inline fun <reified T> CoVariant<T>.asListWithVarargs(vararg ts : T) : CoVariant<Array<out T>> {
  println(this.t)
  return CoVariant(ts)
}

fun <T> CoVariant<T>.asListWithVarargs2(vararg ts : CoVariant<T>) : CoVariant<List<T>> {
  println(this.t)
  return CoVariant(listOf(ts.get(0).t))
}

fun <T> CoVariant<T>.asObfuscatedClass() : CoVariant<Array<Array<ClassThatWillBeObfuscated>>> {
  println(this.t)
  val classThatWillBeObfuscated = ClassThatWillBeObfuscated(9)
  println(classThatWillBeObfuscated.x)
  return CoVariant(arrayOf(arrayOf(classThatWillBeObfuscated)))
}

fun CoVariant<*>.asStar() : CoVariant<*> {
  return this;
}