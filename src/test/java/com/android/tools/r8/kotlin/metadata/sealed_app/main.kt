// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.sealed_app

import com.android.tools.r8.kotlin.metadata.sealed_lib.TestEvent

fun staticDeclaration() {
  println(TestEvent.DiagnosticEvent.DataTestControllerStartEvent("source", "reason"))
}

fun reflectiveUse() {
  println(TestEvent.DiagnosticEvent.DataTestControllerStartEvent::class.supertypes)
}

fun main() {
  staticDeclaration()
  reflectiveUse()
}