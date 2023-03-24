// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

/** Non-API class to allocate compilation failed exceptions without exposing the constructor. */
public final class InternalCompilationFailedExceptionUtils {

  private InternalCompilationFailedExceptionUtils() {}

  public static CompilationFailedException createForTesting() {
    return createForTesting("Compilation failed to complete", null);
  }

  public static CompilationFailedException createForTesting(String message) {
    return createForTesting(message, null);
  }

  public static CompilationFailedException createForTesting(Throwable cause) {
    return createForTesting("Compilation failed to complete", cause);
  }

  public static CompilationFailedException createForTesting(String message, Throwable cause) {
    return create(message, cause, false);
  }

  public static CompilationFailedException create(
      String message, Throwable cause, boolean cancelled) {
    return new CompilationFailedException(message, cause, cancelled);
  }
}
