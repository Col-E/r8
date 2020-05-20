// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;

public class Reporter implements DiagnosticsHandler {

  private final DiagnosticsHandler clientHandler;
  private AbortException abort = null;

  public Reporter() {
    this(new DiagnosticsHandler() {});
  }

  public Reporter(DiagnosticsHandler clientHandler) {
    this.clientHandler = clientHandler;
  }

  @Override
  public synchronized void info(Diagnostic info) {
    clientHandler.info(info);
  }

  @Override
  public synchronized void warning(Diagnostic warning) {
    clientHandler.warning(warning);
  }

  public void warning(String message) {
    warning(new StringDiagnostic(message));
  }

  @Override
  public synchronized void error(Diagnostic error) {
    abort = new AbortException(error);
    clientHandler.error(error);
  }

  public void error(String message) {
    error(new StringDiagnostic(message));
  }

  /**
   * @throws AbortException always.
   */
  public RuntimeException fatalError(String message) {
    throw fatalError(new StringDiagnostic(message));
  }

  /**
   * @throws AbortException always.
   */
  public RuntimeException fatalError(Diagnostic error) {
    error(error);
    throw abort;
  }

  /** @throws AbortException if any error was reported. */
  public synchronized void failIfPendingErrors() {
    if (abort != null) {
      throw new RuntimeException(abort);
    }
  }
}
