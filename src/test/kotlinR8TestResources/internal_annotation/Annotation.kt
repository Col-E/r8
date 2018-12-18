// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package internal_annotation

@Target(AnnotationTarget.CLASS)
internal annotation class Annotation(
    @get:JvmName("f1")
    val field1: Int = 0,
    @get:JvmName("f2")
    val field2: String = "",
    @get:JvmName("f3")
    val field3: IntArray = [],
    @get:JvmName("f4")
    val field4: Array<String> = []
)

@JvmName("foo")
internal fun Base.foo(): StackTraceElement? {
  val anno = getMyAnnotation() ?: return null
  // Note that only Annotation.f(1|2) will be live.
  return StackTraceElement(anno.field2, anno.field2, anno.field2, anno.field1)
}

// To prevent Annotation.f(3|4) from being stripped out by kotlinc
internal fun Base.getUnusedFields(): Array<String>? {
  val anno = getMyAnnotation() ?: return null
  val res = arrayListOf<String>()
  for ((i, idx) in anno.field3.withIndex()) {
    if (idx == i % 8) {
      res.add(anno.field4[i])
    }
  }
  return res.toTypedArray()
}

private fun Base.getMyAnnotation(): Annotation? =
    javaClass.getAnnotation(Annotation::class.java)
