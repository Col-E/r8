// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

public class MethodDiagnostic implements Diagnostic {
  private final DexMethod method;
  private final Origin origin;
  private final String message;

  public MethodDiagnostic(String message, DexMethod method, Origin origin) {
    this.message = message;
    this.method = method;
    this.origin = origin;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    // TODO: support associating a source file line number.
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    return message + " at " + method.qualifiedName();
  }
}
