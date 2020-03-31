// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static kotlinx.metadata.FlagsKt.flagsOf;

import kotlinx.metadata.KmTypeProjection;
import kotlinx.metadata.KmTypeVisitor;
import kotlinx.metadata.KmVariance;

// Provides access to Kotlin information about the type projection of a type (arguments).
public class KotlinTypeProjectionInfo {

  final KmVariance variance;
  final KotlinTypeInfo typeInfo;

  KotlinTypeProjectionInfo(KmVariance variance, KotlinTypeInfo typeInfo) {
    this.variance = variance;
    this.typeInfo = typeInfo;
  }

  static KotlinTypeProjectionInfo create(KmTypeProjection kmTypeProjection) {
    return new KotlinTypeProjectionInfo(
        kmTypeProjection.getVariance(), KotlinTypeInfo.create(kmTypeProjection.getType()));
  }

  public boolean isStarProjection() {
    return variance == null && typeInfo == null;
  }

  public void visit(KmTypeVisitor visitor) {
    KmTypeVisitor kmTypeVisitor = visitor.visitArgument(flagsOf(), variance);
    // TODO(b/152886451): Check if this check should be before visitor.visitArgument(...).
    if (isStarProjection()) {
      kmTypeVisitor.visitStarProjection();
    } else {
      typeInfo.visit(kmTypeVisitor);
    }
  }
}
