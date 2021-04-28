// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnosticinspector;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.utils.StringDiagnostic;

public class FoundStringDiagnosticSubject extends FoundDiagnosticSubject<StringDiagnostic> {

  public FoundStringDiagnosticSubject(StringDiagnostic diagnostic) {
    super(diagnostic);
  }

  public FoundStringDiagnosticSubject assertHasMessage(String expectedMessage) {
    assertEquals(expectedMessage, getDiagnostic().getDiagnosticMessage());
    return this;
  }
}
