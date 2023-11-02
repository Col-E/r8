// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.threading;

import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThrowingAction;
import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class TaskCollection<T> {

  private final ThreadingModule threadingModule;
  private final ExecutorService executorService;

  private final List<Future<T>> futures = new ArrayList<>();

  public TaskCollection(ThreadingModule threadingModule, ExecutorService executorService) {
    this.threadingModule = threadingModule;
    this.executorService = executorService;
  }

  public TaskCollection(InternalOptions options, ExecutorService executorService) {
    this(options.getThreadingModule(), executorService);
  }

  /**
   * Submit a task for execution.
   *
   * <p>The task may start running immediately and on the same thread as the caller, or may run
   * later with completion ensured by a call to {@link #await(Consumer)}.
   *
   * <p>This is the main implementation of adding a task for execution.
   *
   * @param task Task to submit for execution.
   */
  public void submit(Callable<T> task) throws ExecutionException {
    futures.add(threadingModule.submit(task, executorService));
  }

  /**
   * Await the completion of all tasks.
   *
   * <p>This is the main implementation of awaiting task executions.
   *
   * @param consumer Consumer to get each task result. Use null if no results are needed.
   */
  public void await(Consumer<T> consumer) throws ExecutionException {
    threadingModule.awaitFutures(futures);
    if (consumer != null) {
      for (Future<T> future : futures) {
        consumer.accept(Futures.getDone(future));
      }
    }
    futures.clear();
  }

  // Final helper methods for the collection.

  /** Number of current tasks in the collection. */
  public final int size() {
    return futures.size();
  }

  /** True if no tasks are in the collection. */
  public final boolean isEmpty() {
    return size() == 0;
  }

  // Internal getter for subclasses.
  final List<Future<T>> internalGetAndClearFutures() {
    List<Future<T>> copy = new ArrayList<>(futures);
    futures.clear();
    return copy;
  }

  // Internal getter for subclasses.
  final ThreadingModule internalGetThreadingModule() {
    return threadingModule;
  }

  // All methods below are derived from the two primitives above.
  // This ensures that the synchronized impl remains sound.

  /** Derived submit to allow throwing tasks. */
  public final <E extends Exception> void submit(ThrowingAction<E> task) throws ExecutionException {
    submit(
        () -> {
          task.execute();
          return null;
        });
  }

  /** Derived await when no results are needed. */
  public final void await() throws ExecutionException {
    await(null);
  }

  /** Derived await to get all the results in a list. */
  public final List<T> awaitWithResults() throws ExecutionException {
    List<T> results = new ArrayList<>(size());
    await(results::add);
    return results;
  }

  /** Derived await to get a subset of the results in a list. */
  public final List<T> awaitWithResults(Predicate<T> predicate) throws ExecutionException {
    if (predicate == null) {
      return awaitWithResults();
    }
    List<T> filtered = new ArrayList<>();
    await(
        result -> {
          if (predicate.test(result)) {
            filtered.add(result);
          }
        });
    return filtered;
  }
}
