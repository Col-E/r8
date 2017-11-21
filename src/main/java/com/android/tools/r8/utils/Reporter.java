// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.errors.Unreachable;
import java.util.ArrayList;
import java.util.Collection;

public class Reporter implements DiagnosticsHandler {

  private final DiagnosticsHandler clientHandler;
  private int errorCount = 0;
  private final Collection<Throwable> suppressedExceptions = new ArrayList<>();

  public Reporter(DiagnosticsHandler clientHandler) {
    this.clientHandler = clientHandler;
  }

  @Override
  public void info(Diagnostic info) {
    clientHandler.info(info);
  }

  @Override
  public void warning(Diagnostic warning) {
    clientHandler.warning(warning);
  }

  @Override
  public void error(Diagnostic error) {
    clientHandler.error(error);
    synchronized (this) {
      errorCount++;
    }
  }
  public void error(String message) {
    error(new StringDiagnostic(message));
  }

  public void error(Diagnostic error, Throwable suppressedException) {
    clientHandler.error(error);
    synchronized (this) {
      errorCount++;
      suppressedExceptions.add(suppressedException);
    }
  }

  public RuntimeException fatalError(Diagnostic error) throws AbortException {
    error(error);
    failIfPendingErrors();
    throw new Unreachable();
  }

  public RuntimeException fatalError(Diagnostic error, Throwable suppressedException)
      throws AbortException {
    error(error, suppressedException);
    failIfPendingErrors();
    throw new Unreachable();
  }

  public void failIfPendingErrors() throws AbortException {
    synchronized (this) {
      if (errorCount != 0) {
        AbortException abort = new AbortException();
        suppressedExceptions.forEach(throwable -> abort.addSuppressed(throwable));
        throw abort;
      }
    }
  }
}
