// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnosticinspector;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.diagnostic.MissingDefinitionsDiagnostic;
import com.android.tools.r8.utils.StringDiagnostic;

public class FoundDiagnosticSubject<D extends Diagnostic> implements DiagnosticSubject {

  private final D diagnostic;

  public FoundDiagnosticSubject(D diagnostic) {
    this.diagnostic = diagnostic;
  }

  public D getDiagnostic() {
    return diagnostic;
  }

  @Override
  public FoundMissingDefinitionsDiagnosticSubject assertIsMissingDefinitionsDiagnostic() {
    assertThat(diagnostic, diagnosticType(MissingDefinitionsDiagnostic.class));
    return new FoundMissingDefinitionsDiagnosticSubject((MissingDefinitionsDiagnostic) diagnostic);
  }

  @Override
  public FoundStringDiagnosticSubject assertIsStringDiagnostic() {
    assertThat(diagnostic, diagnosticType(StringDiagnostic.class));
    return new FoundStringDiagnosticSubject((StringDiagnostic) diagnostic);
  }
}
