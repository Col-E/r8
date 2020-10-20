// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

class TestDiagnostic implements Diagnostic {

  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  @Override
  public Position getPosition() {
    return null;
  }

  @Override
  public String getDiagnosticMessage() {
    return "Test diagnostic";
  }
}
