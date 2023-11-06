// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

@KeepForApi
public class WhyAreYouNotInliningDiagnostic implements Diagnostic {

  private final Origin origin;
  private final String message;

  public WhyAreYouNotInliningDiagnostic(Origin origin, String message) {
    this.origin = origin;
    this.message = message;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    // We could make this even more precise if we added the current position of the invoke.
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    return message;
  }
}
