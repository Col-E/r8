// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.TypeElement;

public class VerifyTypesHelper {

  private final AppView<?> appView;

  private VerifyTypesHelper(AppView<?> appView) {
    this.appView = appView;
  }

  public static VerifyTypesHelper create(AppView<?> appView) {
    return new VerifyTypesHelper(appView);
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isAssignable(TypeElement one, TypeElement other) {
    if (one.isPrimitiveType() != other.isPrimitiveType()) {
      return false;
    }
    if (one.isPrimitiveType()) {
      assert other.isPrimitiveType();
      return one.equals(other);
    }
    assert one.isReferenceType() && other.isReferenceType();
    if (one.isNullType() && other.isReferenceType()) {
      return true;
    }
    if (one.isArrayType() != other.isArrayType()) {
      return one.isArrayType()
          && other.asClassType().getClassType() == appView.dexItemFactory().objectType;
    }
    if (one.isArrayType()) {
      assert other.isArrayType();
      return isAssignable(one.asArrayType().getMemberType(), other.asArrayType().getMemberType());
    }
    assert one.isClassType() && other.isClassType();
    if (appView.enableWholeProgramOptimizations()) {
      return one.lessThanOrEqual(other, appView)
          || one.isBasedOnMissingClass(appView.withClassHierarchy());
    } else {
      // If we do not have whole program knowledge, we can only do the most basic check.
      if (one.asClassType().getClassType() == appView.dexItemFactory().objectType) {
        return other.asClassType().getClassType() == appView.dexItemFactory().objectType;
      }
    }
    return true;
  }
}
