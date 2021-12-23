// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.crossinline_block_app

import com.android.tools.r8.kotlin.metadata.crossinline_block_lib.bar
import com.android.tools.r8.kotlin.metadata.crossinline_block_lib.foo

fun main(args : Array<String>) {
  if (args.isEmpty()) {
    call("foo")
    bar(bar3 = 0)
  } else {
    call("bar")
  }
}

fun call(name: String) {
  foo { name }
  println(name)
}