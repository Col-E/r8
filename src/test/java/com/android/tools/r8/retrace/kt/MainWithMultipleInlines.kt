// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package retrace

fun main(args: Array<String>) {
  println("Before")
  inlineExceptionStatic {
    throw Exception("Never get's here")
  }
  println("Middle")
  inlineExceptionStatic {
    throw Exception("Never get's here")
  }
  println("After")
}


