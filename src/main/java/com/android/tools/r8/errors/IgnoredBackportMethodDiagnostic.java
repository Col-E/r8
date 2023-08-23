// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.Keep;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.references.MethodReference;

@Keep
public class IgnoredBackportMethodDiagnostic implements DesugarDiagnostic {

  private final DexMethod backport;
  private final Origin origin;
  private final Position position;
  private final int minApiLevel;

  public IgnoredBackportMethodDiagnostic(
      DexMethod backport, Origin origin, Position position, int minApiLevel) {
    this.backport = backport;
    this.origin = origin;
    this.position = position;
    this.minApiLevel = minApiLevel;
  }

  public MethodReference getIgnoredBackportMethod() {
    return backport.asMethodReference();
  }

  public int getConfiguredMinApiLevel() {
    return minApiLevel;
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
    return "Ignored reference to backport "
        + backport.toSourceString()
        + ". The compiler is compiling for min-api "
        + minApiLevel
        + " which includes runtimes that do not support "
        + backport.toSourceString()
        + " but this method will be retained as is (i.e., it is not backported).";
  }
}
