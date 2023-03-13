// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.utils.OptionalBool;

public abstract class MemberResolutionResult<
    D extends DexEncodedMember<D, R>, R extends DexMember<D, R>> {

  public DexClassAndMember<D, R> getResolutionPair() {
    return null;
  }

  public abstract boolean isSuccessfulMemberResolutionResult();

  public abstract SuccessfulMemberResolutionResult<D, R> asSuccessfulMemberResolutionResult();

  public final OptionalBool isAccessibleFrom(
      ProgramDefinition context, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return isAccessibleFrom(context, appView, appView.appInfo());
  }

  public abstract OptionalBool isAccessibleFrom(
      ProgramDefinition context, AppView<?> appView, AppInfoWithClassHierarchy appInfo);

  /**
   * Returns true if resolution failed.
   *
   * <p>Note the disclaimer in the doc of {@code MethodResolutionResult.isSingleResolution()}.
   */
  public boolean isFailedResolution() {
    return false;
  }

  public boolean isFieldResolutionResult() {
    return false;
  }

  public boolean isMethodResolutionResult() {
    return false;
  }

  public FieldResolutionResult asFieldResolutionResult() {
    return null;
  }

  public MethodResolutionResult asMethodResolutionResult() {
    return null;
  }
}
