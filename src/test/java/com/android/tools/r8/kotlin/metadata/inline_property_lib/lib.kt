// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.inline_property_lib

class Lib(val number : Int) {

  val is42
    inline get() = number == 42;
}

class LibExt
  val Lib.is7
    inline get() = number == 7;