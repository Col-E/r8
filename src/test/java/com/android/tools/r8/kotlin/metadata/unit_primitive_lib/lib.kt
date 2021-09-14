// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.unit_primitive_lib

class Lib {
  fun testUnit() : Unit {
    println("testUnit")
  }

  fun testInt() : Int {
    return 42
  }

  fun testIntArray() : IntArray {
    return intArrayOf(42)
  }

  fun testUInt() : UInt {
    return 42u
  }

  fun testUIntArray() : UIntArray {
    return uintArrayOf(42u)
  }
}
