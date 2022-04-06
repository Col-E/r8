// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation.assume;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfo.AssumeType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardMemberRule;

public class AssumeInfoLookup {

  public static AssumeInfo lookupAssumeInfo(
      AppView<AppInfoWithLiveness> appView,
      SingleResolutionResult<?> resolutionResult,
      DexClassAndMethod singleTarget) {
    AssumeInfo resolutionLookup = lookupAssumeInfo(appView, resolutionResult.getResolutionPair());
    if (resolutionLookup == null) {
      return singleTarget != null ? lookupAssumeInfo(appView, singleTarget) : null;
    }
    AssumeInfo singleTargetLookup =
        singleTarget != null ? lookupAssumeInfo(appView, singleTarget) : null;
    return singleTargetLookup != null
        ? resolutionLookup.meet(singleTargetLookup)
        : resolutionLookup;
  }

  public static AssumeInfo lookupAssumeInfo(
      AppView<? extends AppInfoWithClassHierarchy> appView, DexClassAndMember<?, ?> member) {
    DexMember<?, ?> reference = member.getReference();
    ProguardMemberRule assumeNoSideEffectsRule = appView.rootSet().noSideEffects.get(reference);
    ProguardMemberRule assumeValuesRule = appView.rootSet().assumedValues.get(reference);
    if (assumeNoSideEffectsRule == null && assumeValuesRule == null) {
      return null;
    }
    AssumeType type =
        assumeNoSideEffectsRule != null
            ? AssumeType.ASSUME_NO_SIDE_EFFECTS
            : AssumeType.ASSUME_VALUES;
    if ((assumeNoSideEffectsRule != null && assumeNoSideEffectsRule.hasReturnValue())
        || assumeValuesRule == null) {
      return new AssumeInfo(type, assumeNoSideEffectsRule);
    }
    return new AssumeInfo(type, assumeValuesRule);
  }
}
