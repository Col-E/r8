// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import kotlinx.metadata.KmTypeProjection;

public class KmTypeProjectionSubject extends Subject {
  private final CodeInspector codeInspector;
  private final KmTypeProjection kmTypeProjection;

  KmTypeProjectionSubject(CodeInspector codeInspector, KmTypeProjection kmTypeProjection) {
    this.codeInspector = codeInspector;
    this.kmTypeProjection = kmTypeProjection;
  }

  public KmTypeSubject type() {
    return new KmTypeSubject(codeInspector, kmTypeProjection.getType());
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    return type().isRenamed();
  }

  @Override
  public boolean isSynthetic() {
    throw new Unreachable("Cannot determine if a type argument is synthetic");
  }
}
