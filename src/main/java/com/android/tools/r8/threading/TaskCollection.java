// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.threading;

import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThrowingAction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class TaskCollection<T> {

  private final ThreadingModule threadingModule;
  private final ExecutorService executorService;

  private final List<Future<T>> futures = new ArrayList<>();

  public TaskCollection(InternalOptions options, ExecutorService executorService) {
    this.threadingModule = options.getThreadingModule();
    this.executorService = executorService;
  }

  public <E extends Exception> void submit(ThrowingAction<E> task) throws ExecutionException {
    submit(
        () -> {
          task.execute();
          return null;
        });
  }

  public void submit(Callable<T> task) throws ExecutionException {
    futures.add(threadingModule.submit(task, executorService));
  }

  public void await() throws ExecutionException {
    threadingModule.awaitFutures(futures);
  }
}
