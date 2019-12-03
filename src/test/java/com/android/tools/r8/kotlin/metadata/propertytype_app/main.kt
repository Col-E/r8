// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.propertytype_app

import com.android.tools.r8.kotlin.metadata.propertytype_lib.Impl

class ProgramClass : Impl(8) {
}

fun main() {
  val instance : Impl = ProgramClass()
  println(instance.prop1)
}
