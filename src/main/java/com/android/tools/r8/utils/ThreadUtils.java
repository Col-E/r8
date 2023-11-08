// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.threading.ThreadingModule;
import com.android.tools.r8.utils.ListUtils.ReferenceAndIntConsumer;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ThreadUtils {

  public enum WorkLoad {
    // The threshold for HEAVY is basically just a fan-out when we have two items to process.
    HEAVY(2),
    // The threshold for LIGHT has been found by running TiviIncremental benchmark in different
    // configurations. For partitioning inputs in buckets of 3 and use threading on 4 or more was
    // slightly better than threading on 3:
    // Buckets of 3 with threshold 4:
    // TiviIncrementalLibrary(RunTimeRaw): 28076 ms
    // TiviIncrementalMerge(RunTimeRaw): 1429 ms
    // TiviIncrementalProgram(RunTimeRaw): 26374 ms
    // Buckets of 3 with threshold 3:
    // TiviIncrementalLibrary(RunTimeRaw): 30347 ms
    // TiviIncrementalMerge(RunTimeRaw): 1558 ms
    // TiviIncrementalProgram(RunTimeRaw): 26638 ms
    LIGHT(4);

    private final int threshold;

    WorkLoad(int threshold) {
      this.threshold = threshold;
    }

    public int getThreshold() {
      return threshold;
    }
  }

  public static final int NOT_SPECIFIED = -1;

  public static <T, R, E extends Exception> Collection<R> processItemsWithResults(
      Iterable<T> items,
      ThrowingFunction<T, R, E> consumer,
      ThreadingModule threadingModule,
      ExecutorService executorService)
      throws ExecutionException {
    return processItemsWithResults(
        items, (item, i) -> consumer.apply(item), threadingModule, executorService);
  }

  public static <T, R, E extends Exception> Collection<R> processItemsWithResultsThatMatches(
      Iterable<T> items,
      ThrowingFunction<T, R, E> consumer,
      Predicate<R> predicate,
      ThreadingModule threadingModule,
      ExecutorService executorService)
      throws ExecutionException {
    return processItemsWithResultsThatMatches(
        items, (item, i) -> consumer.apply(item), predicate, threadingModule, executorService);
  }

  public static <T, R, E extends Exception> Collection<R> processItemsWithResults(
      Iterable<T> items,
      ThrowingReferenceIntFunction<T, R, E> consumer,
      ThreadingModule threadingModule,
      ExecutorService executorService)
      throws ExecutionException {
    return processItemsWithResults(items::forEach, consumer, threadingModule, executorService);
  }

  public static <T, R, E extends Exception> Collection<R> processItemsWithResultsThatMatches(
      Iterable<T> items,
      ThrowingReferenceIntFunction<T, R, E> consumer,
      Predicate<R> predicate,
      ThreadingModule threadingModule,
      ExecutorService executorService)
      throws ExecutionException {
    return processItemsWithResultsThatMatches(
        items::forEach, consumer, predicate, threadingModule, executorService);
  }

  public static <T, R, E extends Exception> Collection<R> processItemsWithResults(
      ForEachable<T> items,
      ThrowingReferenceIntFunction<T, R, E> consumer,
      ThreadingModule threadingModule,
      ExecutorService executorService)
      throws ExecutionException {
    return processItemsWithResultsThatMatches(
        items, consumer, null, threadingModule, executorService);
  }

  public static <T, R, E extends Exception> Collection<R> processItemsWithResultsThatMatches(
      ForEachable<T> items,
      ThrowingReferenceIntFunction<T, R, E> consumer,
      Predicate<R> predicate,
      ThreadingModule threadingModule,
      ExecutorService executorService)
      throws ExecutionException {
    TaskCollection<R> tasks = new TaskCollection<>(threadingModule, executorService);
    try {
      items.forEachWithIndex(
          (index, item) -> tasks.submitUnchecked(() -> consumer.apply(item, index)));
    } catch (UncheckedExecutionException e) {
      throw e.rethrow();
    }
    return tasks.awaitWithResults(predicate);
  }

  public static <T> void processItems(
      Collection<T> items,
      Consumer<T> consumer,
      ThreadingModule threadingModule,
      ExecutorService executorService)
      throws ExecutionException {
    processItems(
        items,
        (item, i) -> consumer.accept(item),
        threadingModule,
        executorService,
        WorkLoad.LIGHT);
  }

  public static <T> void processItems(
      Collection<T> items,
      ReferenceAndIntConsumer<T> consumer,
      ThreadingModule threadingModule,
      ExecutorService executorService,
      WorkLoad workLoad)
      throws ExecutionException {
    if (items.size() >= workLoad.getThreshold()) {
      processItems(items::forEach, consumer::accept, threadingModule, executorService);
    } else {
      int counter = 0;
      for (T item : items) {
        consumer.accept(item, counter++);
      }
    }
  }

  public static <T, E extends Exception> void processItems(
      ForEachable<T> items,
      ThrowingConsumer<T, E> consumer,
      ThreadingModule threadingModule,
      ExecutorService executorService)
      throws ExecutionException {
    processItems(items, (item, i) -> consumer.accept(item), threadingModule, executorService);
  }

  public static <T, E extends Exception> void processItems(
      ForEachable<T> items,
      ThrowingReferenceIntConsumer<T, E> consumer,
      ThreadingModule threadingModule,
      ExecutorService executorService)
      throws ExecutionException {
    processItemsWithResults(
        items,
        (item, i) -> {
          consumer.accept(item, i);
          return null;
        },
        threadingModule,
        executorService);
  }

  public static <T, U, E extends Exception> void processMap(
      Map<T, U> items,
      ThrowingBiConsumer<T, U, E> consumer,
      ThreadingModule threadingModule,
      ExecutorService executorService)
      throws ExecutionException {
    processMapWithResults(
        items,
        (key, value) -> {
          consumer.accept(key, value);
          return null;
        },
        threadingModule,
        executorService);
  }

  public static <T, U, R, E extends Exception> Collection<R> processMapWithResults(
      Map<T, U> items,
      ThrowingBiFunction<T, U, R, E> consumer,
      ThreadingModule threadingModule,
      ExecutorService executorService)
      throws ExecutionException {
    return processItemsWithResults(
        items.entrySet(),
        arg -> consumer.apply(arg.getKey(), arg.getValue()),
        threadingModule,
        executorService);
  }

  public static <E extends Exception> void processMethods(
      AppView<?> appView,
      ThrowingConsumer<ProgramMethod, E> consumer,
      ThreadingModule threadingModule,
      ExecutorService executorService)
      throws ExecutionException {
    processItems(
        appView.appInfo().classes(),
        clazz -> clazz.forEachProgramMethod(consumer::acceptWithRuntimeException),
        threadingModule,
        executorService);
  }

  static ExecutorService getExecutorServiceForProcessors(
      int processors, ThreadingModule threadingModule) {
    // This heuristic is based on measurements on a 32 core (hyper-threaded) machine.
    int threads = processors <= 2 ? processors : (int) Math.ceil(Integer.min(processors, 16) / 2.0);
    return getExecutorServiceForThreads(threads, threadingModule);
  }

  static ExecutorService getExecutorServiceForThreads(
      int threads, ThreadingModule threadingModule) {
    return threadingModule.createThreadedExecutorService(threads);
  }

  public static ExecutorService getExecutorService(int threads, ThreadingModule threadingModule) {
    return threads == NOT_SPECIFIED
        ? getExecutorServiceForProcessors(
            Runtime.getRuntime().availableProcessors(), threadingModule)
        : getExecutorServiceForThreads(threads, threadingModule);
  }

  public static ExecutorService getExecutorService(InternalOptions options) {
    return getExecutorService(options.threadCount, options.getThreadingModule());
  }

  public static int getNumberOfThreads(ExecutorService service) {
    if (service instanceof ForkJoinPool) {
      return ((ForkJoinPool) service).getParallelism();
    }
    return -1;
  }
}
