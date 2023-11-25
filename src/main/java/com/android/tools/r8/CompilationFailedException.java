// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/**
 * Exception thrown when compilation failed to complete because of errors previously reported
 * through {@link com.android.tools.r8.DiagnosticsHandler}.
 */
@KeepForApi
public class CompilationFailedException extends Exception {

  private final boolean cancelled;

  CompilationFailedException(String message, Throwable cause, boolean cancelled) {
    super(message, cause);
    this.cancelled = cancelled;
  }

  // TODO(b/275715936): Remove this again.
  public CompilationFailedException(String message) {
    super(message);
    cancelled = false;
  }

  /** True if the compilation was cancelled by {@link CancelCompilationChecker} otherwise false. */
  public boolean wasCancelled() {
    return cancelled;
  }
}
