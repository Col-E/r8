// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.UnknownMethodState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.classhierarchy.MethodOverridesCollector;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.Collection;

public class ArgumentPropagatorUnoptimizableMethods {

  private final AppView<AppInfoWithLiveness> appView;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;
  private final MethodStateCollectionByReference methodStates;

  public ArgumentPropagatorUnoptimizableMethods(
      AppView<AppInfoWithLiveness> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      MethodStateCollectionByReference methodStates) {
    this.appView = appView;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
    this.methodStates = methodStates;
  }

  // TODO(b/190154391): Consider if we should bail out for classes that inherit from a missing
  //  class.
  public void initializeUnoptimizableMethodStates(
      Collection<DexProgramClass> stronglyConnectedComponent) {
    ProgramMethodSet unoptimizableVirtualMethods =
        MethodOverridesCollector.findAllMethodsAndOverridesThatMatches(
            appView,
            immediateSubtypingInfo,
            stronglyConnectedComponent,
            method -> {
              if (isUnoptimizableMethod(method)) {
                if (method.getDefinition().belongsToVirtualPool()
                    && !method.getHolder().isFinal()
                    && !method.getAccessFlags().isFinal()) {
                  return true;
                } else {
                  disableArgumentPropagationForMethod(method);
                }
              }
              return false;
            });
    unoptimizableVirtualMethods.forEach(this::disableArgumentPropagationForMethod);
  }

  private void disableArgumentPropagationForMethod(ProgramMethod method) {
    methodStates.set(method, UnknownMethodState.get());
  }

  private boolean isUnoptimizableMethod(ProgramMethod method) {
    assert !method.getDefinition().belongsToVirtualPool()
            || !method.getDefinition().isLibraryMethodOverride().isUnknown()
        : "Unexpected virtual method without library method override information: "
            + method.toSourceString();
    AppInfoWithLiveness appInfo = appView.appInfo();
    InternalOptions options = appView.options();
    return method.getDefinition().isLibraryMethodOverride().isPossiblyTrue()
        || !appInfo.getKeepInfo().getMethodInfo(method).isArgumentPropagationAllowed(options);
  }
}
