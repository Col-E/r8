// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.annotation_lib

import kotlin.reflect.KClass

enum class Direction {
  UP, RIGHT, DOWN, LEFT
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION,
        AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.PROPERTY,
        AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
annotation class AnnoWithClassAndEnum(val clazz : KClass<*>, val direction : Direction)

@Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.TYPEALIAS,
        AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
annotation class AnnoWithClassArr(val classes: Array<KClass<*>>)

class Foo

class Bar @AnnoWithClassArr([Foo::class]) constructor()

@AnnoWithClassArr([Foo::class, Bar::class])
@AnnoWithClassAndEnum(Foo::class, Direction.UP) class Baz {

  @AnnoWithClassAndEnum(Foo::class, Direction.LEFT) val prop : Int = 0

  @AnnoWithClassAndEnum(Foo::class, Direction.RIGHT) fun baz(@AnnoWithClassAndEnum(Foo::class, Direction.DOWN) foo: Int): Int {
    return 1
  }
}

@AnnoWithClassArr([Foo::class, Bar::class])
typealias Qux = Foo

annotation class AnnoNotKept

@Target(AnnotationTarget.TYPE)
annotation class Nested(
  val message: String,
  val kept: AnnoWithClassAndEnum,
  val notKept: AnnoNotKept
)

class Quux {

  fun methodWithTypeAnnotations() : Array<@AnnoWithClassAndEnum(Foo::class, Direction.UP) Int> {
    return arrayOf(1)
  }

  fun methodWithNestedAnnotations() : Array<@Nested("Top most", AnnoWithClassAndEnum(Foo::class, Direction.DOWN), AnnoNotKept()) Int> {
    return arrayOf(1)
  }
}

