// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package unused_singleton

import unused_singleton.TestModule.provideGreeting

internal object TestModule {
  @JvmStatic
  fun provideGreeting() = "Hello"
}

fun main(args: Array<String>) {
  println(provideGreeting())
}
