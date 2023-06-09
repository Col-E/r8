// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.threads;

import com.android.tools.r8.utils.Timing;

public interface ThreadTask {

  void run(Timing timing) throws Exception;

  default void runWithRuntimeException(Timing timing) {
    try {
      run(timing);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  default boolean shouldRun() {
    return true;
  }

  default boolean shouldRunOnThread() {
    return true;
  }
}
