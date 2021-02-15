// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnosticinspector;

import static org.junit.Assert.fail;

import com.android.tools.r8.errors.Unreachable;

public class AbsentDiagnosticSubject implements DiagnosticSubject {
  @Override
  public FoundMissingDefinitionsDiagnosticSubject assertIsMissingDefinitionsDiagnostic() {
    fail("Expected MissingDefinitionsDiagnostic, but was absent");
    throw new Unreachable();
  }
}
