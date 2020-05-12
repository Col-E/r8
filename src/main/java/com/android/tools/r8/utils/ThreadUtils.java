// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

public class ThreadUtils {

  public static final int NOT_SPECIFIED = -1;

  public static <T, R, E extends Exception> Collection<R> processItemsWithResults(
      Iterable<T> items, ThrowingFunction<T, R, E> consumer, ExecutorService executorService)
      throws ExecutionException {
    return processItemsWithResults(items, (item, i) -> consumer.apply(item), executorService);
  }

  public static <T, R, E extends Exception> Collection<R> processItemsWithResults(
      Iterable<T> items,
      ThrowingReferenceIntFunction<T, R, E> consumer,
      ExecutorService executorService)
      throws ExecutionException {
    return processItemsWithResults(items::forEach, consumer, executorService);
  }

  public static <T, R, E extends Exception> Collection<R> processItemsWithResults(
      ForEachable<T> items, ThrowingFunction<T, R, E> consumer, ExecutorService executorService)
      throws ExecutionException {
    return processItemsWithResults(items, (item, i) -> consumer.apply(item), executorService);
  }

  public static <T, R, E extends Exception> Collection<R> processItemsWithResults(
      ForEachable<T> items,
      ThrowingReferenceIntFunction<T, R, E> consumer,
      ExecutorService executorService)
      throws ExecutionException {
    IntBox indexSupplier = new IntBox();
    List<Future<R>> futures = new ArrayList<>();
    items.forEach(
        item -> {
          int index = indexSupplier.getAndIncrement();
          futures.add(executorService.submit(() -> consumer.apply(item, index)));
        });
    return awaitFuturesWithResults(futures);
  }

  public static <T, E extends Exception> void processItems(
      Iterable<T> items, ThrowingConsumer<T, E> consumer, ExecutorService executorService)
      throws ExecutionException {
    processItems(items, (item, i) -> consumer.accept(item), executorService);
  }

  public static <T, E extends Exception> void processItems(
      Iterable<T> items,
      ThrowingReferenceIntConsumer<T, E> consumer,
      ExecutorService executorService)
      throws ExecutionException {
    processItems(items::forEach, consumer, executorService);
  }

  public static <T, E extends Exception> void processItems(
      ForEachable<T> items, ThrowingConsumer<T, E> consumer, ExecutorService executorService)
      throws ExecutionException {
    processItems(items, (item, i) -> consumer.accept(item), executorService);
  }

  public static <T, E extends Exception> void processItems(
      ForEachable<T> items,
      ThrowingReferenceIntConsumer<T, E> consumer,
      ExecutorService executorService)
      throws ExecutionException {
    processItemsWithResults(
        items,
        (item, i) -> {
          consumer.accept(item, i);
          return null;
        },
        executorService);
  }

  public static <T, U, E extends Exception> void processMap(
      Map<T, U> items, ThrowingBiConsumer<T, U, E> consumer, ExecutorService executorService)
      throws ExecutionException {
    processMapWithResults(
        items,
        (key, value) -> {
          consumer.accept(key, value);
          return null;
        },
        executorService);
  }

  public static <T, U, R, E extends Exception> Collection<R> processMapWithResults(
      Map<T, U> items, ThrowingBiFunction<T, U, R, E> consumer, ExecutorService executorService)
      throws ExecutionException {
    return processItemsWithResults(
        items.entrySet(), arg -> consumer.apply(arg.getKey(), arg.getValue()), executorService);
  }

  public static void awaitFutures(Iterable<? extends Future<?>> futures)
      throws ExecutionException {
    Iterator<? extends Future<?>> futureIterator = futures.iterator();
    try {
      while (futureIterator.hasNext()) {
        futureIterator.next().get();
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while waiting for future.", e);
    } finally {
      // In case we get interrupted or one of the threads throws an exception, still wait for all
      // further work to make sure synchronization guarantees are met. Calling cancel unfortunately
      // does not guarantee that the task at hand actually terminates before cancel returns.
      while (futureIterator.hasNext()) {
        try {
          futureIterator.next().get();
        } catch (Throwable t) {
          // Ignore any new Exception.
        }
      }
    }
  }

  public static <R> Collection<R> awaitFuturesWithResults(Collection<? extends Future<R>> futures)
      throws ExecutionException {
    List<R> results = new ArrayList<>(futures.size());
    Iterator<? extends Future<R>> futureIterator = futures.iterator();
    try {
      while (futureIterator.hasNext()) {
        results.add(futureIterator.next().get());
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while waiting for future.", e);
    } finally {
      // In case we get interrupted or one of the threads throws an exception, still wait for all
      // further work to make sure synchronization guarantees are met. Calling cancel unfortunately
      // does not guarantee that the task at hand actually terminates before cancel returns.
      while (futureIterator.hasNext()) {
        try {
          futureIterator.next().get();
        } catch (Throwable t) {
          // Ignore any new Exception.
        }
      }
    }
    return results;
  }

  static ExecutorService getExecutorServiceForProcessors(int processors) {
    // This heuristic is based on measurements on a 32 core (hyper-threaded) machine.
    int threads = processors <= 2 ? processors : (int) Math.ceil(Integer.min(processors, 16) / 2.0);
    return getExecutorServiceForThreads(threads);
  }

  static ExecutorService getExecutorServiceForThreads(int threads) {
    // Note Executors.newSingleThreadExecutor() is not used when just one thread is used. See
    // b/67338394.
    return Executors.newWorkStealingPool(threads);
  }

  public static ExecutorService getExecutorService(int threads) {
    return threads == NOT_SPECIFIED
        ? getExecutorServiceForProcessors(Runtime.getRuntime().availableProcessors())
        : getExecutorServiceForThreads(threads);
  }

  public static ExecutorService getExecutorService(InternalOptions options) {
    return getExecutorService(options.threadCount);
  }

  public static int getNumberOfThreads(ExecutorService service) {
    if (service instanceof ForkJoinPool) {
      return ((ForkJoinPool) service).getParallelism();
    }
    return -1;
  }
}
