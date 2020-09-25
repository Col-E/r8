// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.utils.OptionalBool;

public abstract class MemberResolutionResult<
    D extends DexEncodedMember<D, R>, R extends DexMember<D, R>> {

  public abstract boolean isSuccessfulMemberResolutionResult();

  public abstract SuccessfulMemberResolutionResult<D, R> asSuccessfulMemberResolutionResult();

  public abstract OptionalBool isAccessibleFrom(
      ProgramDefinition context, AppInfoWithClassHierarchy appInfo);

  public final OptionalBool isAccessibleFrom(
      ProgramDefinition context, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return isAccessibleFrom(context, appView.appInfo());
  }
}
