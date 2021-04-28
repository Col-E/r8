// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.DiagnosticsLevel;

public class NopDiagnosticsHandler implements DiagnosticsHandler {

  @Override
  public void error(Diagnostic error) {
    // Intentionally empty.
  }

  @Override
  public void warning(Diagnostic warning) {
    // Intentionally empty.
  }

  @Override
  public void info(Diagnostic info) {
    // Intentionally empty.
  }

  @Override
  public DiagnosticsLevel modifyDiagnosticsLevel(DiagnosticsLevel level, Diagnostic diagnostic) {
    return level;
  }
}
