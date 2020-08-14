// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.sealed_lib

/** Code originating from this bug b/163359809 */

interface Log {
  val source: String
}

sealed class TestEvent(
  open var stamp: Long = currentStamp
) {
  sealed class DiagnosticEvent(
    open val name: String,
    open val display: Boolean = false,
    override var stamp: Long = currentStamp
  ) : TestEvent(stamp) {
    data class DataTestControllerStartEvent(
      override val source: String,
      override val name: String = DataTestControllerStartEvent::class.java.simpleName,
      override val display: Boolean = false,
      override var stamp: Long = currentStamp
    ) :
      DiagnosticEvent(name, display, stamp),
      Log
  }
  companion object {
    var currentStamp = 0L
  }
}
