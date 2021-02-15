// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnosticinspector;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.diagnostic.MissingDefinitionsDiagnostic;
import com.android.tools.r8.references.ClassReference;

public class FoundMissingDefinitionsDiagnosticSubject
    extends FoundDiagnosticSubject<MissingDefinitionsDiagnostic> {

  public FoundMissingDefinitionsDiagnosticSubject(MissingDefinitionsDiagnostic diagnostic) {
    super(diagnostic);
  }

  public FoundMissingDefinitionsDiagnosticSubject assertHasMessage(String expectedMessage) {
    assertEquals(expectedMessage, getDiagnostic().getDiagnosticMessage());
    return this;
  }

  public FoundMissingDefinitionsDiagnosticSubject assertIsMissingClass(Class<?> clazz) {
    return this;
  }

  public FoundMissingDefinitionsDiagnosticSubject assertIsMissingClass(
      ClassReference classReference) {
    return this;
  }

  public FoundMissingDefinitionsDiagnosticSubject assertNumberOfMissingClasses(int expected) {
    assertEquals(expected, getDiagnostic().getMissingDefinitions().size());
    return this;
  }
}
