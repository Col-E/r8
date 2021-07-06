// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.androidapi.AndroidApiReferenceLevelCache;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.info.DefaultFieldOptimizationWithMinApiInfo;
import com.android.tools.r8.ir.optimize.info.DefaultMethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.DefaultMethodOptimizationWithMinApiInfo;
import com.android.tools.r8.ir.optimize.info.MemberOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.shaking.DefaultEnqueuerUseRegistry;
import com.android.tools.r8.utils.AndroidApiLevel;

public class ApiModelAnalysis extends EnqueuerAnalysis {

  private final AppView<?> appView;
  private final AndroidApiLevel minApiLevel;
  private final AndroidApiReferenceLevelCache referenceLevelCache;

  public ApiModelAnalysis(AppView<?> appView, AndroidApiReferenceLevelCache referenceLevelCache) {
    this.appView = appView;
    this.minApiLevel = appView.options().minApiLevel;
    this.referenceLevelCache = referenceLevelCache;
  }

  @Override
  public void processNewlyLiveField(ProgramField field, ProgramDefinition context) {
    setApiLevelForMemberDefinition(
        field.getDefinition(), computeApiLevelForReferencedTypes(field.getReference()));
  }

  @Override
  public void processNewlyLiveMethod(ProgramMethod method, ProgramDefinition context) {
    setApiLevelForMemberDefinition(
        method.getDefinition(), computeApiLevelForReferencedTypes(method.getReference()));
  }

  @Override
  public void processTracedCode(ProgramMethod method, DefaultEnqueuerUseRegistry registry) {
    assert registry.getMaxApiReferenceLevel().isGreaterThanOrEqualTo(minApiLevel);
    if (appView.options().apiModelingOptions().tracedMethodApiLevelCallback != null) {
      appView
          .options()
          .apiModelingOptions()
          .tracedMethodApiLevelCallback
          .accept(method.getMethodReference(), registry.getMaxApiReferenceLevel());
    }
    setApiLevelForMemberDefinition(
        method.getDefinition(), computeApiLevelForReferencedTypes(method.getReference()));
    setApiLevelForCode(method.getDefinition(), registry.getMaxApiReferenceLevel());
  }

  private void setApiLevelForMemberDefinition(
      DexEncodedMember<?, ?> member, AndroidApiLevel apiLevel) {
    // To not have mutable update information for all members that all has min api level we
    // swap the default optimization info for one with that marks the api level to be min api.
    MemberOptimizationInfo<?> optimizationInfo = member.getOptimizationInfo();
    if (!optimizationInfo.isMutableOptimizationInfo() && apiLevel == minApiLevel) {
      member.accept(
          field -> {
            field.setMinApiOptimizationInfo(DefaultFieldOptimizationWithMinApiInfo.getInstance());
          },
          method -> {
            method.setMinApiOptimizationInfo(DefaultMethodOptimizationWithMinApiInfo.getInstance());
          });
    } else {
      AndroidApiLevel maxApiLevel =
          optimizationInfo.hasApiReferenceLevelForDefinition()
              ? apiLevel.max(optimizationInfo.getApiReferenceLevelForDefinition(minApiLevel))
              : apiLevel;
      member.accept(
          field -> {
            field.getMutableOptimizationInfo().setApiReferenceLevelForDefinition(maxApiLevel);
          },
          method -> {
            method.getMutableOptimizationInfo().setApiReferenceLevelForDefinition(maxApiLevel);
          });
    }
  }

  private void setApiLevelForCode(DexEncodedMethod method, AndroidApiLevel apiLevel) {
    MethodOptimizationInfo optimizationInfo = method.getOptimizationInfo();
    assert optimizationInfo != DefaultMethodOptimizationInfo.getInstance();
    if (!optimizationInfo.isMutableOptimizationInfo() && apiLevel == minApiLevel) {
      assert optimizationInfo == DefaultMethodOptimizationWithMinApiInfo.getInstance();
      return;
    }
    method.getMutableOptimizationInfo().setApiReferenceLevelForCode(apiLevel);
  }

  private AndroidApiLevel computeApiLevelForReferencedTypes(DexMember<?, ?> member) {
    return member.computeApiLevelForReferencedTypes(appView, referenceLevelCache::lookupMax);
  }
}
