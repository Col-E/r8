// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package lambdas_jstyle_runnable.inner

import lambdas_jstyle_runnable.publish

private val reachableChecker : Runnable by lazy {
  Runnable {
    if (!Thread.currentThread().isInterrupted) {
      publish("reachableChecker")
    }
  }
}

class Implementer4 {
  fun getRunnable() : Runnable {
    return reachableChecker
  }
}