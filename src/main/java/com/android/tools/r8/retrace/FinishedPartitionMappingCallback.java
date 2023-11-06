// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.keepanno.annotations.KeepForApi;

/***
 * Interface for registering a callback when a retracing operation is finished.
 */
@FunctionalInterface
@KeepForApi
public interface FinishedPartitionMappingCallback {

  FinishedPartitionMappingCallback EMPTY_INSTANCE = diagnosticsHandler -> {};

  static FinishedPartitionMappingCallback empty() {
    return EMPTY_INSTANCE;
  }

  void finished(DiagnosticsHandler handler);
}
