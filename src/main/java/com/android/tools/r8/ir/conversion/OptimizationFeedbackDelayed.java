// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexEncodedMethod.ClassInlinerEligibility;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer;
import com.android.tools.r8.graph.ParameterUsagesInfo;
import com.android.tools.r8.graph.UpdatableOptimizationInfo;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import java.util.BitSet;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

public class OptimizationFeedbackDelayed implements OptimizationFeedback {

  // Caching of updated optimization info and processed status.
  private final Map<DexEncodedMethod, UpdatableOptimizationInfo> optimizationInfos =
      new IdentityHashMap<>();
  private final Map<DexEncodedMethod, ConstraintWithTarget> processed = new IdentityHashMap<>();

  private synchronized UpdatableOptimizationInfo getOptimizationInfoForUpdating(
      DexEncodedMethod method) {
    UpdatableOptimizationInfo info = optimizationInfos.get(method);
    if (info != null) {
      return info;
    }
    info = method.getOptimizationInfo().mutableCopy();
    optimizationInfos.put(method, info);
    return info;
  }

  @Override
  public synchronized void methodReturnsArgument(DexEncodedMethod method, int argument) {
    getOptimizationInfoForUpdating(method).markReturnsArgument(argument);
  }

  @Override
  public synchronized void methodReturnsConstant(DexEncodedMethod method, long value) {
    getOptimizationInfoForUpdating(method).markReturnsConstant(value);
  }

  @Override
  public synchronized void methodNeverReturnsNull(DexEncodedMethod method) {
    getOptimizationInfoForUpdating(method).markNeverReturnsNull();
  }

  @Override
  public synchronized void methodNeverReturnsNormally(DexEncodedMethod method) {
    getOptimizationInfoForUpdating(method).markNeverReturnsNormally();
  }

  @Override
  public synchronized void markProcessed(DexEncodedMethod method, ConstraintWithTarget state) {
    processed.put(method, state);
  }

  @Override
  public void markUseIdentifierNameString(DexEncodedMethod method) {
    getOptimizationInfoForUpdating(method).markUseIdentifierNameString();
  }

  @Override
  public synchronized void markCheckNullReceiverBeforeAnySideEffect(
      DexEncodedMethod method, boolean mark) {
    getOptimizationInfoForUpdating(method).markCheckNullReceiverBeforeAnySideEffect(mark);
  }

  @Override
  public synchronized void markTriggerClassInitBeforeAnySideEffect(
      DexEncodedMethod method, boolean mark) {
    getOptimizationInfoForUpdating(method).markTriggerClassInitBeforeAnySideEffect(mark);
  }

  @Override
  public synchronized void setClassInlinerEligibility(
      DexEncodedMethod method, ClassInlinerEligibility eligibility) {
    getOptimizationInfoForUpdating(method).setClassInlinerEligibility(eligibility);
  }

  @Override
  public synchronized void setTrivialInitializer(DexEncodedMethod method, TrivialInitializer info) {
    getOptimizationInfoForUpdating(method).setTrivialInitializer(info);
  }

  @Override
  public synchronized void setInitializerEnablingJavaAssertions(DexEncodedMethod method) {
    getOptimizationInfoForUpdating(method).setInitializerEnablingJavaAssertions();
  }

  @Override
  public synchronized void setParameterUsages(
      DexEncodedMethod method, ParameterUsagesInfo parameterUsagesInfo) {
    getOptimizationInfoForUpdating(method).setParameterUsages(parameterUsagesInfo);
  }

  @Override
  public synchronized void setNonNullParamHints(DexEncodedMethod method, BitSet hints) {
    getOptimizationInfoForUpdating(method).setNonNullParamHints(hints);
  }

  public void updateVisibleOptimizationInfo(Collection<DexEncodedMethod> methods) {
    optimizationInfos.forEach(DexEncodedMethod::setOptimizationInfo);
    processed.forEach(DexEncodedMethod::markProcessed);
    optimizationInfos.clear();
    processed.clear();
  }
}
