// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.utils.ListUtils;
import java.util.ArrayList;
import java.util.List;

// Helper to check that a particular error occurred.
class DiagnosticsChecker implements DiagnosticsHandler {
  public List<Diagnostic> errors = new ArrayList<>();
  public List<Diagnostic> warnings = new ArrayList<>();
  public List<Diagnostic> infos = new ArrayList<>();

  @Override
  public void error(Diagnostic error) {
    errors.add(error);
  }

  @Override
  public void warning(Diagnostic warning) {
    warnings.add(warning);
  }

  @Override
  public void info(Diagnostic info) {
    infos.add(info);
  }

  public interface FailingRunner {
    void run(DiagnosticsHandler handler) throws CompilationFailedException;
  }

  public static void checkErrorsContains(String snippet, FailingRunner runner)
      throws CompilationFailedException {
    DiagnosticsChecker handler = new DiagnosticsChecker();
    try {
      runner.run(handler);
    } catch (CompilationFailedException e) {
      assertTrue(
          "Expected to find snippet '"
              + snippet
              + "' in error messages:\n"
              + String.join("\n", ListUtils.map(handler.errors, Diagnostic::getDiagnosticMessage)),
          handler.errors.stream().anyMatch(d -> d.getDiagnosticMessage().contains(snippet)));
      throw e;
    }
  }
}
