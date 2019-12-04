// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package retrace

inline fun inlineExceptionStatic(f: () -> Unit) {
  println("in inlineExceptionStatic")
  throw java.lang.Exception("inlineExceptionStatic")
  println("will not be printed")
}

class InlineFunction {
  inline fun inlineExceptionInstance(f: () -> Unit) {
    println("in inlineExceptionInstance")
    throw java.lang.Exception("inlineExceptionInstance")
    println("will not be printed")
  }
}