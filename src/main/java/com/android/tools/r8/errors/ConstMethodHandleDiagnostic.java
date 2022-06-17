// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.utils.AndroidApiLevel;

public class ConstMethodHandleDiagnostic extends UnsupportedFeatureDiagnostic {

  public ConstMethodHandleDiagnostic(Origin origin, MethodPosition position) {
    super("const-method-handle", AndroidApiLevel.P, origin, position);
  }

  @Override
  public String getDiagnosticMessage() {
    return UnsupportedFeatureDiagnostic.makeMessage(
        AndroidApiLevel.P, "Const-method-handle", getPosition().toString());
  }
}
