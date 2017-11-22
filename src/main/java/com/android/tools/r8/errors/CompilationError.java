// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.origin.Origin;

/**
 * Exception to signal an compilation error.
 * <p>
 * This is always an expected error and considered a user input issue. A user-understandable message
 * must be provided.
 */
public class CompilationError extends RuntimeException {

  public CompilationError(String message) {
    super(message);
  }

  public CompilationError(String message, Throwable cause) {
    super(message, cause);
  }

  public CompilationError(String message, Origin origin) {
    super(origin + ": " + message);
  }
}