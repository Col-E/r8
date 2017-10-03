// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

/**
 * A DiagnosticsHandler can be provided to customize handling of diagnostics information.
 *
 * <p>During compilation the error, warning and info methods will be called.
 */
public interface DiagnosticsHandler {

  /**
   * Handle error diagnostics.
   *
   * <p>By default this throws the exception.
   *
   * @param error CompilationException containing error information.
   * @throws CompilationException
   */
  default void error(CompilationException error) throws CompilationException {
    throw error;
  }

  /**
   * Handle warning diagnostics.
   *
   * @param warning Diagnostic containing warning information.
   */
  default void warning(Diagnostic warning) {
    System.err.println(warning.toString());
  }

  /**
   * Handle info diagnostics.
   *
   * @param info Diagnostic containing the information.
   */
  default void info(Diagnostic info) {
    System.out.println(info.toString());
  }
}
