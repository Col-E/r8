// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static com.android.tools.r8.DiagnosticsLevel.ERROR;
import static com.android.tools.r8.DiagnosticsLevel.INFO;
import static com.android.tools.r8.DiagnosticsLevel.WARNING;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.DiagnosticsLevel;
import com.android.tools.r8.errors.Unreachable;
import java.util.ArrayList;
import java.util.List;

public class Reporter implements DiagnosticsHandler {

  private static class DiagnosticsLevelMapping {
    private final DiagnosticsLevel from;
    private final DiagnosticsLevel to;
    private final String diagnosticsClassName;

    public DiagnosticsLevelMapping(
        DiagnosticsLevel from, DiagnosticsLevel to, String diagnosticsClassName) {
      this.from = from;
      this.to = to;
      this.diagnosticsClassName = diagnosticsClassName;
    }

    public DiagnosticsLevel map(DiagnosticsLevel level, Diagnostic diagnostic) {
      if (level != from) {
        return level;
      }
      if (diagnosticsClassName.length() == 0
          || diagnosticsClassName.equals(diagnostic.getClass().getSimpleName())
          || diagnosticsClassName.equals(diagnostic.getClass().getTypeName())) {
        return to;
      }
      // Some diagnostics are exposed as interfaces, and implemented by internal implementations.
      for (Class<?> clazz : diagnostic.getClass().getInterfaces()) {
        if (diagnosticsClassName.equals(clazz.getSimpleName())
            || diagnosticsClassName.equals(clazz.getTypeName())) {
          return to;
        }
      }
      return level;
    }
  }

  private final DiagnosticsHandler clientHandler;
  private final List<DiagnosticsLevelMapping> diagnosticsLevelMapping = new ArrayList<>();
  private AbortException abort = null;

  public Reporter() {
    this(new DiagnosticsHandler() {});
  }

  public Reporter(DiagnosticsHandler clientHandler) {
    this.clientHandler = clientHandler;
  }

  private void handleDiagnostic(DiagnosticsLevel level, Diagnostic diagnostic) {
    // To avoid having an entry for fatal error in the public  API enum use null to signal
    // fatal error internally.
    if (level != null) {
      DiagnosticsLevel modifiedLevel = clientHandler.modifyDiagnosticsLevel(level, diagnostic);
      level = modifiedLevel != null ? modifiedLevel : level;
      level = mapDiagnosticsLevel(level, diagnostic);
    } else {
      level = ERROR;
    }
    switch (level) {
      case INFO:
        clientHandler.info(diagnostic);
        break;
      case WARNING:
        clientHandler.warning(diagnostic);
        break;
      case ERROR:
        abort = new AbortException(diagnostic);
        clientHandler.error(diagnostic);
        break;
      case NONE:
        break;
      default:
        throw new Unreachable();
    }
  }

  @Override
  public synchronized void info(Diagnostic info) {
    handleDiagnostic(INFO, info);
  }

  public void info(String message) {
    info(new StringDiagnostic(message));
  }

  @Override
  public synchronized void warning(Diagnostic warning) {
    handleDiagnostic(WARNING, warning);
  }

  public void warning(String message) {
    warning(new StringDiagnostic(message));
  }

  @Override
  public synchronized void error(Diagnostic error) {
    handleDiagnostic(ERROR, error);
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
    handleDiagnostic(null, error);
    throw abort;
  }

  /** @throws AbortException if any error was reported. */
  public synchronized void failIfPendingErrors() {
    if (abort != null) {
      throw new RuntimeException(abort);
    }
  }

  private DiagnosticsLevel mapDiagnosticsLevel(DiagnosticsLevel level, Diagnostic diagnostic) {
    for (DiagnosticsLevelMapping diagnosticsLevelMapping : diagnosticsLevelMapping) {
      level = diagnosticsLevelMapping.map(level, diagnostic);
    }
    return level;
  }

  public void addDiagnosticsLevelMapping(
      DiagnosticsLevel from, String diagnosticsClassName, DiagnosticsLevel to) {
    diagnosticsLevelMapping.add(new DiagnosticsLevelMapping(from, to, diagnosticsClassName));
  }
}
