// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.BaseCompilerCommand;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.MainDexOverflow;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import java.util.ArrayList;
import java.util.Collection;

public class Reporter implements DiagnosticsHandler {

  private final DiagnosticsHandler clientHandler;
  private final BaseCompilerCommand command;
  private int errorCount = 0;
  private Diagnostic lastError;
  private final Collection<Throwable> suppressedExceptions = new ArrayList<>();

  public Reporter(DiagnosticsHandler clientHandler) {
    this(clientHandler, null);
  }

  public Reporter(DiagnosticsHandler clientHandler, BaseCompilerCommand command) {
    this.clientHandler = clientHandler;
    this.command = command;
  }

  @Override
  public synchronized void info(Diagnostic info) {
    clientHandler.info(info);
  }

  @Override
  public synchronized void warning(Diagnostic warning) {
    clientHandler.warning(warning);
  }

  @Override
  public synchronized void error(Diagnostic error) {
    clientHandler.error(error);
    lastError = error;
    errorCount++;
  }

  public void error(String message) {
    error(new StringDiagnostic(message));
  }

  public synchronized void error(Diagnostic error, Throwable suppressedException) {
    clientHandler.error(error);
    lastError = error;
    errorCount++;
    suppressedExceptions.add(suppressedException);
  }

  /**
   * @throws AbortException always.
   */
  public RuntimeException fatalError(Diagnostic error) {
    error(error);
    failIfPendingErrors();
    throw new Unreachable();
  }

  /**
   * @throws AbortException always.
   */
  public RuntimeException fatalError(Diagnostic error, Throwable suppressedException) {
    error(error, suppressedException);
    failIfPendingErrors();
    throw new Unreachable();
  }

  /**
   * @throws AbortException always.
   */
  public RuntimeException fatalError(MainDexOverflow e) {
    if (command instanceof R8Command) {
      return fatalError(new StringDiagnostic(e.getMessageForR8()));
    } else if (command instanceof D8Command) {
      return fatalError(new StringDiagnostic(e.getMessageForD8()));
    } else {
      return fatalError(new StringDiagnostic(e.getMessage()));
    }
  }

  /**
   * @throws AbortException if any error was reported.
   */
  public void failIfPendingErrors() {
    synchronized (this) {
      if (errorCount != 0) {
        AbortException abort;
        if (lastError != null && lastError.getDiagnosticMessage() != null) {
          StringBuilder builder = new StringBuilder("Error: ");
          if (lastError.getOrigin() != Origin.unknown()) {
            builder.append(lastError.getOrigin()).append(", ");
          }
          if (lastError.getPosition() != Position.UNKNOWN) {
            builder.append(lastError.getPosition()).append(", ");
          }
          builder.append(lastError.getDiagnosticMessage());
          abort = new AbortException(builder.toString());
        } else {
          abort = new AbortException();
        }
        throw addSuppressedExceptions(abort);
      }
    }
  }

  private <T extends Throwable> T addSuppressedExceptions(T t) {
    suppressedExceptions.forEach(throwable -> t.addSuppressed(throwable));
    return t;
  }

  public void guard(Runnable action) throws CompilationFailedException {
    try {
      action.run();
    } catch (CompilationError e) {
      error(e);
      throw addSuppressedExceptions(new CompilationFailedException());
    } catch (AbortException e) {
      throw new CompilationFailedException(e);
    }
  }
}
