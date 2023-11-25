// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

/** Common type for all diagnostics related to constant-dynamic desugaring. */
@KeepForApi
public class ConstantDynamicDesugarDiagnostic implements DesugarDiagnostic {

  private final Origin origin;
  private final Position position;
  private final String message;

  public ConstantDynamicDesugarDiagnostic(Origin origin, Position position, String message) {
    this.origin = origin;
    this.position = position;
    this.message = message;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return position;
  }

  @Override
  public String getDiagnosticMessage() {
    return message;
  }
}
