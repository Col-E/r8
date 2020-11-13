// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.references.PackageReference;

class TraceReferencesPrintUsage implements TraceReferencesConsumer {

  private final TraceReferencesResult.Builder traceReferencesResultBuilder =
      TraceReferencesResult.builder();
  private boolean finishedCalled = false;

  @Override
  public void acceptType(TracedClass type, DiagnosticsHandler handler) {
    assert !finishedCalled;
    traceReferencesResultBuilder.acceptType(type, handler);
  }

  @Override
  public void acceptField(TracedField field, DiagnosticsHandler handler) {
    assert !finishedCalled;
    traceReferencesResultBuilder.acceptField(field, handler);
  }

  @Override
  public void acceptMethod(TracedMethod method, DiagnosticsHandler handler) {
    assert !finishedCalled;
    traceReferencesResultBuilder.acceptMethod(method, handler);
  }

  @Override
  public void acceptPackage(PackageReference pkg, DiagnosticsHandler handler) {
    assert !finishedCalled;
    traceReferencesResultBuilder.acceptPackage(pkg, handler);
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    assert !finishedCalled;
    finishedCalled = true;
  }

  public String get() {
    Formatter formatter = new PrintUsesFormatter();
    formatter.format(traceReferencesResultBuilder.build());
    return formatter.get();
  }
}
