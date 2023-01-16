// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.Keep;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

@Keep
public class BackportDiagnostic implements DesugarDiagnostic {

  private final DexMethod backport;
  private final Origin origin;
  private final Position position;

  public BackportDiagnostic(DexMethod backport, Origin origin, Position position) {
    this.backport = backport;
    this.origin = origin;
    this.position = position;
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
    return "Attempt to backport " + backport.toSourceString();
  }
}
