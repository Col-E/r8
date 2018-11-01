// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package lambdas_jstyle_runnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RunnableRunner {

  private ThreadPoolExecutor executor;
  private List<Future> futures;

  RunnableRunner() {
    executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
    futures = new ArrayList<Future>();
  }

  void submit(Runnable runnable) {
    futures.add(executor.submit(runnable));
  }

  int size() {
    return executor.getActiveCount();
  }

  void waitFutures() {
    Iterator<Future> it = futures.iterator();
    try {
      while (it.hasNext()) {
        it.next().get(1, TimeUnit.MILLISECONDS);
      }
    } catch (Exception e) {
      // Ignore for testing.
    } finally {
      while (it.hasNext()) {
        try {
          it.next().get();
        } catch (Throwable t) {
          // Ignore too.
        }
      }
      executor.shutdownNow();
    }
  }
}
