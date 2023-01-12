// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.LookupTarget;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.DefaultEnqueuerUseRegistry;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.EnqueuerWorklist;

public class ApiModelAnalysis extends EnqueuerAnalysis {

  private final AppView<?> appView;
  private final AndroidApiLevelCompute apiCompute;
  private final ComputedApiLevel minApiLevel;

  public ApiModelAnalysis(AppView<?> appView) {
    this.appView = appView;
    this.apiCompute = appView.apiLevelCompute();
    this.minApiLevel = appView.computedMinApiLevel();
  }

  @Override
  public void processNewlyLiveField(
      ProgramField field, ProgramDefinition context, EnqueuerWorklist worklist) {
    computeAndSetApiLevelForDefinition(field);
  }

  @Override
  public void processNewlyLiveMethod(
      ProgramMethod method,
      ProgramDefinition context,
      Enqueuer enqueuer,
      EnqueuerWorklist worklist) {
    computeAndSetApiLevelForDefinition(method);
  }

  @Override
  public void processTracedCode(
      ProgramMethod method, DefaultEnqueuerUseRegistry registry, EnqueuerWorklist worklist) {
    assert registry.getMaxApiReferenceLevel().isGreaterThanOrEqualTo(minApiLevel);
    if (appView.options().apiModelingOptions().tracedMethodApiLevelCallback != null) {
      appView
          .options()
          .apiModelingOptions()
          .tracedMethodApiLevelCallback
          .accept(method.getMethodReference(), registry.getMaxApiReferenceLevel());
    }
    computeAndSetApiLevelForDefinition(method);
    method.getDefinition().setApiLevelForCode(registry.getMaxApiReferenceLevel());
  }

  @Override
  public void notifyMarkMethodAsTargeted(ProgramMethod method, EnqueuerWorklist worklist) {
    computeAndSetApiLevelForDefinition(method);
  }

  @Override
  public void notifyMarkFieldAsReachable(ProgramField field, EnqueuerWorklist worklist) {
    computeAndSetApiLevelForDefinition(field);
  }

  @Override
  public void processNewLiveNonProgramType(ClasspathOrLibraryClass clazz) {
    clazz.forEachClassMethod(this::computeAndSetApiLevelForDefinition);
  }

  @Override
  public void notifyMarkVirtualDispatchTargetAsLive(
      LookupTarget target, EnqueuerWorklist worklist) {
    target.accept(
        lookupMethodTarget -> computeAndSetApiLevelForDefinition(lookupMethodTarget.getTarget()),
        lookupLambdaTarget -> {
          // The implementation method will be assigned an api level when visited.
        });
  }

  @Override
  public void notifyFailedMethodResolutionTarget(
      DexEncodedMethod method, EnqueuerWorklist worklist) {
    // We may not trace into failed resolution targets.
    method.setApiLevelForCode(ComputedApiLevel.unknown());
  }

  private void computeAndSetApiLevelForDefinition(DexClassAndMember<?, ?> member) {
    member
        .getDefinition()
        .setApiLevelForDefinition(
            apiCompute.computeApiLevelForDefinition(
                member.getReference(), appView.dexItemFactory(), ComputedApiLevel.unknown()));
  }
}
