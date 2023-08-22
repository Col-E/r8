// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.sealed.kt

sealed interface SealedInterfaceSubClasses {
  object A : SealedInterfaceSubClasses {
    override fun toString() : String { return "I am A" }
  }
  object B : SealedInterfaceSubClasses {
    override fun toString() : String { return "I am B" }
  }
}

fun f(o : SealedInterfaceSubClasses) {
  when (o) {
    SealedInterfaceSubClasses.A -> print("an A: ")
    SealedInterfaceSubClasses.B -> print("a B: ")
  }
  println(o);
}

fun main() {
  f(SealedInterfaceSubClasses.A)
  f(SealedInterfaceSubClasses.B)
}
