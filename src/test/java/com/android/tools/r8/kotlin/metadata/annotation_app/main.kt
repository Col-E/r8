// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.annotation_app

import com.android.tools.r8.kotlin.metadata.annotation_lib.AnnoWithClassAndEnum
import com.android.tools.r8.kotlin.metadata.annotation_lib.AnnoWithClassArr
import com.android.tools.r8.kotlin.metadata.annotation_lib.Bar
import com.android.tools.r8.kotlin.metadata.annotation_lib.Baz
import com.android.tools.r8.kotlin.metadata.annotation_lib.Nested
import com.android.tools.r8.kotlin.metadata.annotation_lib.Quux
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters

fun main() {
  Bar::class.primaryConstructor?.annotations?.get(0)?.printAnnoWithClassArr()
  Baz::class.annotations.get(0).printAnnoWithClassArr()
  Baz::class.annotations.get(1).printAnnoWithClassAndEnum()
  Baz::prop.annotations.get(0).printAnnoWithClassAndEnum()
  Baz::baz.annotations.get(0).printAnnoWithClassAndEnum()
  Baz::baz.valueParameters.get(0).annotations.get(0).printAnnoWithClassAndEnum()
  // We cannot reflect on annotations on typealiases:
  // https://youtrack.jetbrains.com/issue/KT-21489
  Quux::methodWithTypeAnnotations
      .returnType.arguments.get(0).type?.annotations?.get(0)?.printAnnoWithClassAndEnum()
  val nested = Quux::methodWithNestedAnnotations.returnType.arguments[0].type?.annotations?.get(0) as Nested
  println(nested.message)
  nested.kept.printAnnoWithClassAndEnum()
  if (nested::class::memberProperties.get().any { it.name.equals("notKept") }) {
    println("com.android.tools.r8.kotlin.metadata.annotation_lib.Foo")
  } else {
    println("a.b.c")
  }
}

fun Annotation.printAnnoWithClassArr() {
  val annoWithClassArr = this as AnnoWithClassArr
  annoWithClassArr.classes.forEach { println(it) }
}

fun Annotation.printAnnoWithClassAndEnum() {
  val annoWithClassAndEnum = this as AnnoWithClassAndEnum
  println(annoWithClassAndEnum.clazz)
  println(annoWithClassAndEnum.direction)
}