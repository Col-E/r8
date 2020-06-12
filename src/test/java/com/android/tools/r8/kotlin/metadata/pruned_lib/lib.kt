// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.pruned_lib

// The Base class will be removed during
open class Base

class Sub : Base() {

  fun notKept() : Boolean {
    return true
  }

  fun kept() : Int {
    return 42
  }
}

