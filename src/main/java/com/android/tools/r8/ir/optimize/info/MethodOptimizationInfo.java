// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.DexEncodedMethod.ClassInlinerEligibility;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.optimize.info.ParameterUsagesInfo.ParameterUsage;
import java.util.BitSet;
import java.util.Set;

public interface MethodOptimizationInfo {

  enum InlinePreference {
    NeverInline,
    ForceInline,
    Default
  }

  boolean cannotBeKept();

  boolean classInitializerMayBePostponed();

  TypeLatticeElement getDynamicReturnType();

  ParameterUsage getParameterUsages(int parameter);

  BitSet getNonNullParamOrThrow();

  BitSet getNonNullParamOnNormalExits();

  boolean hasBeenInlinedIntoSingleCallSite();

  boolean isReachabilitySensitive();

  boolean returnsArgument();

  int getReturnedArgument();

  boolean neverReturnsNull();

  boolean neverReturnsNormally();

  boolean returnsConstant();

  boolean returnsConstantNumber();

  boolean returnsConstantString();

  ClassInlinerEligibility getClassInlinerEligibility();

  Set<DexType> getInitializedClassesOnNormalExit();

  TrivialInitializer getTrivialInitializerInfo();

  boolean isInitializerEnablingJavaAssertions();

  long getReturnedConstantNumber();

  DexString getReturnedConstantString();

  boolean forceInline();

  boolean neverInline();

  boolean useIdentifierNameString();

  boolean checksNullReceiverBeforeAnySideEffect();

  boolean triggersClassInitBeforeAnySideEffect();

  boolean mayHaveSideEffects();

  boolean returnValueOnlyDependsOnArguments();

  boolean returnValueHasBeenPropagated();

  UpdatableMethodOptimizationInfo mutableCopy();
}
