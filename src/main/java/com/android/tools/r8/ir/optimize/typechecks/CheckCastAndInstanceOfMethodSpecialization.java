// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.typechecks;

import static com.android.tools.r8.graph.AccessControl.isClassAccessible;
import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;

/**
 * An optimization that merges a method override (B.m()) into the method it overrides (A.m()).
 *
 * <p>If the method and its override effectively implement an 'instanceof B' instruction, i.e.,
 * A.m() returns false and B.m() returns true, then B.m() is removed and the body of A.m() is
 * updated to {@code return this instanceof B}.
 *
 * <p>If the method and its override implement 'not instanceof B', then B.m() is removed and the
 * body of A.m() is updated to {@code return !(this instance B)}.
 *
 * <p>TODO(b/151596599): Also handle methods that implement casts.
 */
public class CheckCastAndInstanceOfMethodSpecialization implements Action {

  private static final OptimizationFeedbackSimple feedback =
      OptimizationFeedbackSimple.getInstance();

  private final AppView<AppInfoWithLiveness> appView;
  private final IRConverter converter;

  private final SortedProgramMethodSet candidatesForInstanceOfOptimization =
      SortedProgramMethodSet.create();

  public CheckCastAndInstanceOfMethodSpecialization(
      AppView<AppInfoWithLiveness> appView, IRConverter converter) {
    assert !appView.options().debug;
    this.appView = appView;
    this.converter = converter;
  }

  public void addCandidateForOptimization(ProgramMethod method, AbstractValue abstractReturnValue) {
    if (!converter.isInWave()) {
      return;
    }
    if (isCandidateForInstanceOfOptimization(method, abstractReturnValue)) {
      synchronized (this) {
        if (candidatesForInstanceOfOptimization.isEmpty()) {
          converter.addWaveDoneAction(this);
        }
        candidatesForInstanceOfOptimization.add(method);
      }
    }
  }

  private boolean isCandidateForInstanceOfOptimization(
      ProgramMethod method, AbstractValue abstractReturnValue) {
    return method.getReference().getReturnType().isBooleanType()
        && abstractReturnValue.isSingleBoolean();
  }

  @Override
  public void execute() {
    assert !candidatesForInstanceOfOptimization.isEmpty();
    ProgramMethodSet processed = ProgramMethodSet.create();
    for (ProgramMethod method : candidatesForInstanceOfOptimization) {
      if (!processed.contains(method)) {
        processCandidateForInstanceOfOptimization(method);
      }
    }
  }

  private void processCandidateForInstanceOfOptimization(ProgramMethod method) {
    DexEncodedMethod definition = method.getDefinition();
    if (!definition.isNonPrivateVirtualMethod()) {
      return;
    }

    MethodOptimizationInfo optimizationInfo = method.getDefinition().getOptimizationInfo();
    if (optimizationInfo.mayHaveSideEffects()) {
      return;
    }

    AbstractValue abstractReturnValue = optimizationInfo.getAbstractReturnValue();
    if (!abstractReturnValue.isSingleBoolean()) {
      return;
    }

    ProgramMethod parentMethod = resolveOnSuperClass(method);
    if (parentMethod == null || !parentMethod.getDefinition().isNonPrivateVirtualMethod()) {
      return;
    }

    MethodOptimizationInfo parentOptimizationInfo =
        parentMethod.getDefinition().getOptimizationInfo();
    if (parentOptimizationInfo.mayHaveSideEffects()) {
      return;
    }

    AbstractValue abstractParentReturnValue = parentOptimizationInfo.getAbstractReturnValue();
    if (!abstractParentReturnValue.isSingleBoolean()) {
      return;
    }

    // Verify that the methods are not pinned. They shouldn't be, since we've computed an abstract
    // return value for both.
    assert !appView.appInfo().isPinned(method.getReference());
    assert !appView.appInfo().isPinned(parentMethod.getReference());

    if (appView
        .appInfo()
        .getObjectAllocationInfoCollection()
        .hasInstantiatedStrictSubtype(method.getHolder())) {
      return;
    }

    DexEncodedMethod parentMethodDefinition = parentMethod.getDefinition();
    if (abstractParentReturnValue == abstractReturnValue) {
      // The parent method is already guaranteed to return the same value.
    } else if (isClassAccessible(method.getHolder(), parentMethod, appView).isTrue()) {
      parentMethodDefinition.setCode(
          parentMethodDefinition.buildInstanceOfCode(
              method.getHolderType(), abstractParentReturnValue.isTrue(), appView.options()),
          appView);
      // Rebuild inlining constraints.
      IRCode code =
          parentMethodDefinition.getCode().buildIR(parentMethod, appView, parentMethod.getOrigin());
      converter.markProcessed(code, feedback);
      // Fixup method optimization info (the method no longer returns a constant).
      feedback.unsetAbstractReturnValue(parentMethod.getDefinition());
    } else {
      return;
    }

    method.getHolder().removeMethod(method.getReference());
  }

  private ProgramMethod resolveOnSuperClass(ProgramMethod method) {
    DexProgramClass superClass =
        asProgramClassOrNull(appView.definitionFor(method.getHolder().superType));
    if (superClass == null) {
      return null;
    }

    SingleResolutionResult resolutionResult =
        appView.appInfo().resolveMethodOn(superClass, method.getReference()).asSingleResolution();
    if (resolutionResult == null) {
      return null;
    }
    return resolutionResult.getResolvedProgramMethod();
  }
}
