// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.threading;

import com.android.tools.r8.utils.InternalOptions;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class SynchronizedTaskCollection<T> extends TaskCollection<T> {

  public SynchronizedTaskCollection(InternalOptions options, ExecutorService executorService) {
    super(options, executorService);
  }

  @Override
  public synchronized void submit(Callable<T> task) throws ExecutionException {
    super.submit(task);
  }

  @Override
  public void await() throws ExecutionException {
    // Assuming tasks may add new tasks, awaiting all pending tasks must be run in a loop.
    // The identification of futures is synchronized with submit so that we don't have concurrent
    // modification of the task list.
    List<Future<T>> futures;
    synchronized (this) {
      futures = getAndClearFutures();
    }
    do {
      getThreadingModule().awaitFutures(futures);
      synchronized (this) {
        futures = getAndClearFutures();
      }
    } while (!futures.isEmpty());
  }
}
