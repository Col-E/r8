// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/**
 * Exception thrown when retrace failed to complete because of errors previously reported through
 * {@link com.android.tools.r8.DiagnosticsHandler}.
 */
@KeepForApi
public class RetraceFailedException extends Exception {

  public RetraceFailedException() {
    super("Retrace failed to complete");
  }

  public RetraceFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
