// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.DexEncodedMethod.ClassInlinerEligibility;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import java.util.BitSet;
import java.util.Set;

public interface UpdatableMethodOptimizationInfo extends MethodOptimizationInfo {

  void markInitializesClassesOnNormalExit(Set<DexType> initializedClasses);

  void markReturnsArgument(int argument);

  void markReturnsConstantNumber(long value);

  void markReturnsConstantString(DexString value);

  void markReturnsObjectOfType(TypeLatticeElement type);

  void markMayNotHaveSideEffects();

  void markNeverReturnsNull();

  void markNeverReturnsNormally();

  void markCheckNullReceiverBeforeAnySideEffect(boolean mark);

  void markTriggerClassInitBeforeAnySideEffect(boolean mark);

  void setClassInlinerEligibility(ClassInlinerEligibility eligibility);

  void setTrivialInitializer(TrivialInitializer info);

  void setInitializerEnablingJavaAssertions();

  void setParameterUsages(ParameterUsagesInfo parameterUsagesInfo);

  void setNonNullParamOrThrow(BitSet facts);

  void setNonNullParamOnNormalExits(BitSet facts);

  void setReachabilitySensitive(boolean reachabilitySensitive);

  void markUseIdentifierNameString();

  void markForceInline();

  void unsetForceInline();

  void markNeverInline();
}
