// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.crossinline_anon_app

import com.android.tools.r8.kotlin.metadata.crossinline_anon_lib.Context
import com.android.tools.r8.kotlin.metadata.crossinline_anon_lib.Handler

fun main() {
  Handler({ context, throwable ->
    println(context)
  }).handle(object : Context {
    override fun toString(): String {
      return "foo"
    }
  }, NullPointerException())
}