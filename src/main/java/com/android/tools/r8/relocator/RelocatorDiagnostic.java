// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.relocator;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

class RelocatorDiagnostic implements Diagnostic {

  private final String message;

  public RelocatorDiagnostic(String message) {
    this.message = message;
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
    return message;
  }

  static RelocatorDiagnostic typeRelocateAmbiguous(DexType type) {
    return new RelocatorDiagnostic(
        "Type '" + type.toSourceString() + "' can be relocated by multiple mappings.");
  }
}
