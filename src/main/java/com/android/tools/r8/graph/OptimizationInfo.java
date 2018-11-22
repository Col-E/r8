// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.DexEncodedMethod.ClassInlinerEligibility;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer;
import com.android.tools.r8.graph.ParameterUsagesInfo.ParameterUsage;
import java.util.BitSet;

public interface OptimizationInfo {

  enum InlinePreference {
    NeverInline,
    ForceInline,
    Default
  }

  ParameterUsage getParameterUsages(int parameter);

  BitSet getNonNullParamHints();

  boolean isReachabilitySensitive();

  boolean returnsArgument();

  int getReturnedArgument();

  boolean neverReturnsNull();

  boolean neverReturnsNormally();

  boolean returnsConstant();

  ClassInlinerEligibility getClassInlinerEligibility();

  TrivialInitializer getTrivialInitializerInfo();

  boolean isInitializerEnablingJavaAssertions();

  long getReturnedConstant();

  boolean isPublicized();

  boolean forceInline();

  boolean neverInline();

  boolean useIdentifierNameString();

  boolean checksNullReceiverBeforeAnySideEffect();

  boolean triggersClassInitBeforeAnySideEffect();

  UpdatableOptimizationInfo mutableCopy();
}
