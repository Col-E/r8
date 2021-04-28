// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.tracereferences;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.diagnostic.internal.MissingClassInfoImpl;
import com.android.tools.r8.diagnostic.internal.MissingDefinitionsDiagnosticImpl;
import com.android.tools.r8.references.PackageReference;

public class TraceReferencesCheckConsumer implements TraceReferencesConsumer {

  private final MissingDefinitionsDiagnosticImpl.Builder diagnosticBuilder =
      MissingDefinitionsDiagnosticImpl.builder();

  @Override
  public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {
    if (tracedClass.isMissingDefinition()) {
      MissingClassInfoImpl.Builder missingClassInfoBuilder =
          MissingClassInfoImpl.builder().setClass(tracedClass.getReference());
      missingClassInfoBuilder.addReferencedFromContext();
      diagnosticBuilder.addMissingDefinitionInfo(missingClassInfoBuilder.build());
    }
  }

  @Override
  public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {
    if (tracedField.isMissingDefinition()) {}
  }

  @Override
  public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
    if (tracedMethod.isMissingDefinition()) {}
  }

  @Override
  public void acceptPackage(PackageReference pkg, DiagnosticsHandler handler) {
    // Intentionally empty.
  }

  @Override
  public void finished(DiagnosticsHandler handler) {}
}
