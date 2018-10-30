// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package lambdas_jstyle_runnable

import lambdas_jstyle_runnable.inner.Delegator3
import lambdas_jstyle_runnable.inner.Implementer4
import java.util.ArrayDeque
import java.util.Queue

private val queue : Queue<String> = ArrayDeque<String>()

fun publish(message: String) {
  queue.add(message)
}

fun main(args: Array<String>) {
  assert(queue.isEmpty())
  val runner = RunnableRunner()
  runner.submit(Implementer1().getRunnable())
  runner.submit(Implementer2().getRunnable())
  runner.submit(Delegator3(Thread.currentThread().priority))
  if (runner.size() > 2) {
    runner.submit(Implementer4().getRunnable())
  }
  runner.waitFutures()
  assert(queue.size == 3)
}
