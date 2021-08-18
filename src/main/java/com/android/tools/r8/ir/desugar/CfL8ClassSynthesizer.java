// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public interface CfL8ClassSynthesizer {

  // Each instance may delegate class synthesis to other threads, R8 needs to wait on the futures
  // to be sure all classes have been instantiated.
  List<Future<?>> synthesizeClasses(
      ExecutorService executorService, CfL8ClassSynthesizerEventConsumer eventConsumer);
}
