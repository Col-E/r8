// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.pruned_lib

annotation class NeverInline

// The Base class will be removed during compilation
open class Base

class Sub : Base() {

  var notExposedProperty : Int = 42
  var keptProperty : String = "Hello World";

  fun notKept() : Boolean {
    return true
  }

  @NeverInline
  fun keptWithoutPinning() : Int {
    if (System.currentTimeMillis() == 0L) {
      return 41;
    }
    return 42;
  }

  fun kept() : Int {
    if (System.currentTimeMillis() > 0) {
      notExposedProperty = 0
      keptProperty = "Goodbye World"
    }
    return keptWithoutPinning()
  }
}

class SubUser {

  @NeverInline
  fun use(s : Sub) {
    println(s.notExposedProperty)
    println(s.keptProperty)
  }
}