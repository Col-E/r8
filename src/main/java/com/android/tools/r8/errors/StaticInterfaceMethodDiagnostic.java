// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.ApiLevelException;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.AndroidApiLevel;

public class StaticInterfaceMethodDiagnostic extends ApiLevelDiagnostic {

  private final MethodPosition position;

  public StaticInterfaceMethodDiagnostic(MethodPosition position) {
    assert position != null;
    this.position = position;
  }

  @Override
  public Position getPosition() {
    return position;
  }

  @Override
  public String getDiagnosticMessage() {
    return ApiLevelException.makeMessage(
        AndroidApiLevel.N, "Static interface methods", position.toString());
  }
}
