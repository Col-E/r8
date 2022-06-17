// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.AndroidApiLevel;

public class InvokeCustomDiagnostic extends UnsupportedFeatureDiagnostic {

  public InvokeCustomDiagnostic(Origin origin, Position position) {
    super("invoke-custom", AndroidApiLevel.O, origin, position);
  }

  @Override
  public String getDiagnosticMessage() {
    return UnsupportedFeatureDiagnostic.makeMessage(AndroidApiLevel.O, "Invoke-customs", null);
  }
}
