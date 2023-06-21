// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.threads;

import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.Timing.TimingMerger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ThreadTaskUtils {

  public static void processTasks(
      ExecutorService executorService,
      InternalOptions options,
      TimingMerger timingMerger,
      ThreadTask... tasks)
      throws ExecutionException {
    assert tasks.length > 0;
    List<Future<Void>> futures = new ArrayList<>(tasks.length);
    if (timingMerger.isEmpty()) {
      for (ThreadTask task : tasks) {
        if (task.shouldRun()) {
          processTask(executorService, task, futures);
        }
      }
      ThreadUtils.awaitFutures(futures);
    } else {
      List<Timing> timings =
          Arrays.asList(ArrayUtils.filled(new Timing[tasks.length], Timing.empty()));
      int taskIndex = 0;
      for (ThreadTask task : tasks) {
        if (task.shouldRun()) {
          processTaskWithTiming(executorService, options, task, taskIndex++, futures, timings);
        }
      }
      ThreadUtils.awaitFutures(futures);
      timingMerger.add(timings);
      timingMerger.end();
    }
    for (ThreadTask task : tasks) {
      if (task.shouldRun()) {
        task.onJoin();
      }
    }
  }

  private static void processTask(
      ExecutorService executorService, ThreadTask task, List<Future<Void>> futures) {
    if (task.shouldRunOnThread()) {
      ThreadUtils.processAsynchronously(
          () -> task.runWithRuntimeException(Timing.empty()), executorService, futures);
    } else {
      task.runWithRuntimeException(Timing.empty());
    }
  }

  private static void processTaskWithTiming(
      ExecutorService executorService,
      InternalOptions options,
      ThreadTask task,
      int taskIndex,
      List<Future<Void>> futures,
      List<Timing> timings) {
    if (task.shouldRunOnThread()) {
      ThreadUtils.processAsynchronously(
          () -> executeTask(options, task, taskIndex, timings), executorService, futures);
    } else {
      executeTask(options, task, taskIndex, timings);
    }
  }

  private static void executeTask(
      InternalOptions options, ThreadTask task, int taskIndex, List<Timing> timings) {
    Timing threadTiming = Timing.create("Timing", options);
    timings.set(taskIndex, threadTiming);
    threadTiming.begin("Task " + (taskIndex + 1));
    task.runWithRuntimeException(threadTiming);
    threadTiming.end();
    threadTiming.end();
  }
}
