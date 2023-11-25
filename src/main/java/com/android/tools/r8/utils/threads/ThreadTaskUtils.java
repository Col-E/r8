// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.threads;

import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.Timing.TimingMerger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class ThreadTaskUtils {

  public static void processTasks(
      ExecutorService executorService,
      InternalOptions options,
      TimingMerger timingMerger,
      ThreadTask... tasks)
      throws ExecutionException {
    assert tasks.length > 0;
    TaskCollection<?> taskCollection = new TaskCollection<>(options, executorService, tasks.length);
    if (timingMerger.isEmpty()) {
      for (ThreadTask task : tasks) {
        if (task.shouldRun()) {
          processTask(task, taskCollection);
        }
      }
      taskCollection.await();
    } else {
      List<Timing> timings =
          Arrays.asList(ArrayUtils.filled(new Timing[tasks.length], Timing.empty()));
      int taskIndex = 0;
      for (ThreadTask task : tasks) {
        if (task.shouldRun()) {
          processTaskWithTiming(options, task, taskIndex++, taskCollection, timings);
        }
      }
      taskCollection.await();
      timingMerger.add(timings);
      timingMerger.end();
    }
    for (ThreadTask task : tasks) {
      if (task.shouldRun()) {
        task.onJoin();
      }
    }
  }

  private static void processTask(ThreadTask task, TaskCollection<?> taskCollection)
      throws ExecutionException {
    if (task.shouldRunOnThread()) {
      taskCollection.submit(() -> task.runWithRuntimeException(Timing.empty()));
    } else {
      task.runWithRuntimeException(Timing.empty());
    }
  }

  private static void processTaskWithTiming(
      InternalOptions options,
      ThreadTask task,
      int taskIndex,
      TaskCollection<?> taskCollection,
      List<Timing> timings)
      throws ExecutionException {
    if (task.shouldRunOnThread()) {
      taskCollection.submit(() -> executeTask(options, task, taskIndex, timings));
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
