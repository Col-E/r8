// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.threading;

import com.android.tools.r8.Keep;
import com.android.tools.r8.errors.Unreachable;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Threading module interface to enable non-blocking usage of R8.
 *
 * <p>The threading module has multiple implementations outside the main R8 jar. The concrete
 * implementations are loaded via a Java service loader. Since these implementations are dynamically
 * loaded the interface they implement must be kept.
 */
@Keep
public interface ThreadingModule {
  <T> Future<T> submit(Callable<T> task, ExecutorService executorService) throws ExecutionException;

  <T> void awaitFutures(List<Future<T>> futures) throws ExecutionException;

  class Loader {

    public static ThreadingModuleProvider load() {
      ServiceLoader<ThreadingModuleProvider> providers =
          ServiceLoader.load(ThreadingModuleProvider.class);
      // Don't use `Optional findFirst()` here as it hits a desugared-library issue.
      Iterator<ThreadingModuleProvider> iterator = providers.iterator();
      if (iterator.hasNext()) {
        return iterator.next();
      }
      throw new Unreachable("Failure to service-load a provider for the threading module");
    }
  }
}
