// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.position.Position;

public class CfCodeDiagnostics implements Diagnostic {

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return methodPosition;
  }

  @Override
  public String getDiagnosticMessage() {
    return diagnosticMessage;
  }

  private final Origin origin;
  private final MethodPosition methodPosition;
  private final String diagnosticMessage;

  public CfCodeDiagnostics(Origin origin, DexMethod method, String diagnosticMessage) {
    this.origin = origin;
    this.methodPosition = new MethodPosition(method.asMethodReference());
    this.diagnosticMessage = diagnosticMessage;
  }
}
