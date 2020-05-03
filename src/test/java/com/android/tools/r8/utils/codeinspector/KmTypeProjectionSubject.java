// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import kotlinx.metadata.KmTypeProjection;
import kotlinx.metadata.KmVariance;

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

  public KmVariance variance() {
    return kmTypeProjection.getVariance();
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

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof KmTypeProjectionSubject)) {
      return false;
    }
    return areEqual(this.kmTypeProjection, ((KmTypeProjectionSubject) obj).kmTypeProjection);
  }

  public static boolean areEqual(KmTypeProjection one, KmTypeProjection other) {
    if (one == null && other == null) {
      return true;
    }
    if (one == null || other == null) {
      return false;
    }
    if (one.getVariance() != other.getVariance()) {
      return false;
    }
    return KmTypeSubject.areEqual(one.getType(), other.getType(), true);
  }
}
