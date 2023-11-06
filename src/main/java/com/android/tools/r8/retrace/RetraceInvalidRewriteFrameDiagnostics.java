// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

@KeepForApi
public class RetraceInvalidRewriteFrameDiagnostics implements Diagnostic {

  private final int numberOfFramesToRemove;
  private final String method;

  private RetraceInvalidRewriteFrameDiagnostics(int numberOfFramesToRemove, String method) {
    this.numberOfFramesToRemove = numberOfFramesToRemove;
    this.method = method;
  }

  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    return "Cannot remove "
        + numberOfFramesToRemove
        + " frames from the retraced output of "
        + method
        + " because it exceeds the number of retraced frames";
  }

  public static RetraceInvalidRewriteFrameDiagnostics create(
      int numberOfFramesToRemove, String method) {
    return new RetraceInvalidRewriteFrameDiagnostics(numberOfFramesToRemove, method);
  }
}
