// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import java.util.ArrayList;
import java.util.List;

public class TestDiagnosticMessagesImpl implements DiagnosticsHandler, TestDiagnosticMessages {
  private final List<Diagnostic> infos = new ArrayList<>();
  private final List<Diagnostic> warnings = new ArrayList<>();
  private final List<Diagnostic> errors = new ArrayList<>();

  @Override
  public void info(Diagnostic info) {
    infos.add(info);
  }

  @Override
  public void warning(Diagnostic warning) {
    warnings.add(warning);
  }

  @Override
  public void error(Diagnostic error) {
    errors.add(error);
  }

  public List<Diagnostic> getInfos() {
    return infos;
  }

  public List<Diagnostic> getWarnings() {
    return warnings;
  }

  public List<Diagnostic> getErrors() {
    return errors;
  }
}
