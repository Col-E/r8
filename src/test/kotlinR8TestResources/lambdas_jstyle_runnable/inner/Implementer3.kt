// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package lambdas_jstyle_runnable.inner

import lambdas_jstyle_runnable.publish

private val innerRunnable = Runnable {
  if (!Thread.currentThread().isInterrupted) {
    publish("innerRunnable")
  }
}

open class Implementer3(private val priority : Int) : Runnable {
  override fun run() {
    Thread.currentThread().priority = priority
    innerRunnable.run()
  }
}

class Delegator3(priority: Int) : Implementer3(priority)