// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.reprocessingcriteria;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePrimitiveTypeParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteReferenceTypeParameterState;
import com.android.tools.r8.optimize.argumentpropagation.utils.WideningUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

/**
 * Represents that a parameter should be reprocessed under certain conditions if we have non-trivial
 * information about it (e.g., abstract value, dynamic type, nullability).
 *
 * <p>Example: If we determine that a parameter should not be reprocessed if we only have
 * non-trivial information about its dynamic type, then an instance of this class is used which
 * returns false in {@link #shouldReprocessDueToDynamicType()}.
 */
public class NonTrivialParameterReprocessingCriteria extends ParameterReprocessingCriteria {

  public NonTrivialParameterReprocessingCriteria(boolean reprocessDueToDynamicType) {
    assert !reprocessDueToDynamicType;
  }

  @Override
  public boolean shouldReprocess(
      AppView<AppInfoWithLiveness> appView,
      ConcreteParameterState parameterState,
      DexType parameterType) {
    if (parameterState.isReferenceParameter()) {
      return shouldReprocess(appView, parameterState.asReferenceParameter(), parameterType);
    } else {
      assert parameterState.isPrimitiveParameter();
      return shouldReprocess(appView, parameterState.asPrimitiveParameter(), parameterType);
    }
  }

  @Override
  public boolean shouldReprocessDueToAbstractValue() {
    return true;
  }

  @Override
  public boolean shouldReprocessDueToDynamicType() {
    return false;
  }

  @Override
  public boolean shouldReprocessDueToNullability() {
    return true;
  }

  private boolean shouldReprocess(
      AppView<AppInfoWithLiveness> appView,
      ConcretePrimitiveTypeParameterState parameterState,
      DexType parameterType) {
    return true;
  }

  private boolean shouldReprocess(
      AppView<AppInfoWithLiveness> appView,
      ConcreteReferenceTypeParameterState parameterState,
      DexType parameterType) {
    if (shouldReprocessDueToAbstractValue()
        && !parameterState.getAbstractValue(appView).isUnknown()) {
      return true;
    }
    if (shouldReprocessDueToDynamicType()) {
      DynamicType widenedDynamicType =
          WideningUtils.widenDynamicNonReceiverType(
              appView,
              parameterState.getDynamicType().withNullability(Nullability.maybeNull()),
              parameterType);
      if (!widenedDynamicType.isUnknown()) {
        return true;
      }
    }
    if (shouldReprocessDueToNullability()
        && !parameterState.isReceiverParameter()
        && !parameterState.getNullability().isUnknown()) {
      return true;
    }
    return false;
  }
}
