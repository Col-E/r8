// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.threading.providers.blocking;

import com.android.tools.r8.threading.ThreadingModule;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ThreadingModuleBlocking implements ThreadingModule {

  @Override
  public <T> Future<T> submit(Callable<T> task, ExecutorService executorService) {
    return executorService.submit(task);
  }

  @Override
  public <T> void awaitFutures(List<Future<T>> futures) throws ExecutionException {
    Iterator<? extends Future<?>> it = futures.iterator();
    try {
      while (it.hasNext()) {
        it.next().get();
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while waiting for future.", e);
    } finally {
      // In case we get interrupted or one of the threads throws an exception, still wait for all
      // further work to make sure synchronization guarantees are met. Calling cancel unfortunately
      // does not guarantee that the task at hand actually terminates before cancel returns.
      while (it.hasNext()) {
        try {
          it.next().get();
        } catch (Throwable t) {
          // Ignore any new Exception.
        }
      }
    }
  }
}
