// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import java.util.ArrayList;
import java.util.List;

public class KeepingDiagnosticHandler implements DiagnosticsHandler {
  public final List<Diagnostic> infos = new ArrayList<>();
  public final List<Diagnostic> warnings = new ArrayList<>();
  public final List<Diagnostic> errors = new ArrayList<>();

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
}
