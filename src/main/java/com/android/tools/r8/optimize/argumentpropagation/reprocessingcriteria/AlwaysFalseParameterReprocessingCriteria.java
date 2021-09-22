// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.reprocessingcriteria;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteParameterState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

/**
 * Represents that a parameter should never be reprocessed even if we have non-trivial information
 * about it (e.g., abstract value, dynamic type, nullability).
 *
 * <p>Example: This is used for unused parameters.
 */
public class AlwaysFalseParameterReprocessingCriteria extends ParameterReprocessingCriteria {

  public static final AlwaysFalseParameterReprocessingCriteria INSTANCE =
      new AlwaysFalseParameterReprocessingCriteria();

  private AlwaysFalseParameterReprocessingCriteria() {}

  public static AlwaysFalseParameterReprocessingCriteria get() {
    return INSTANCE;
  }

  @Override
  public boolean isNeverReprocess() {
    return true;
  }

  @Override
  public boolean shouldReprocess(
      AppView<AppInfoWithLiveness> appView,
      ConcreteParameterState parameterState,
      DexType parameterType) {
    return false;
  }

  @Override
  public boolean shouldReprocessDueToAbstractValue() {
    return false;
  }

  @Override
  public boolean shouldReprocessDueToDynamicType() {
    return false;
  }

  @Override
  public boolean shouldReprocessDueToNullability() {
    return false;
  }
}
