// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexEncodedMethod.ClassInlinerEligibility;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.ClassTypeLatticeElement;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.info.ParameterUsagesInfo;
import java.util.BitSet;
import java.util.Set;

public interface MethodOptimizationFeedback {

  void markForceInline(DexEncodedMethod method);

  void markInlinedIntoSingleCallSite(DexEncodedMethod method);

  void markMethodCannotBeKept(DexEncodedMethod method);

  void methodInitializesClassesOnNormalExit(
      DexEncodedMethod method, Set<DexType> initializedClasses);

  void methodReturnsArgument(DexEncodedMethod method, int argument);

  void methodReturnsConstantNumber(DexEncodedMethod method, long value);

  void methodReturnsConstantString(DexEncodedMethod method, DexString value);

  void methodReturnsObjectOfType(
      DexEncodedMethod method, AppView<?> appView, TypeLatticeElement type);

  void methodReturnsObjectWithLowerBoundType(DexEncodedMethod method, ClassTypeLatticeElement type);

  void methodMayNotHaveSideEffects(DexEncodedMethod method);

  void methodReturnValueOnlyDependsOnArguments(DexEncodedMethod method);

  void methodNeverReturnsNull(DexEncodedMethod method);

  void methodNeverReturnsNormally(DexEncodedMethod method);

  void markProcessed(DexEncodedMethod method, ConstraintWithTarget state);

  void markAsPropagated(DexEncodedMethod method);

  void markUseIdentifierNameString(DexEncodedMethod method);

  void markCheckNullReceiverBeforeAnySideEffect(DexEncodedMethod method, boolean mark);

  void markTriggerClassInitBeforeAnySideEffect(DexEncodedMethod method, boolean mark);

  void setClassInlinerEligibility(DexEncodedMethod method, ClassInlinerEligibility eligibility);

  void setTrivialInitializer(DexEncodedMethod method, TrivialInitializer info);

  void setInitializerEnablingJavaAssertions(DexEncodedMethod method);

  void setParameterUsages(DexEncodedMethod method, ParameterUsagesInfo parameterUsagesInfo);

  void setNonNullParamOrThrow(DexEncodedMethod method, BitSet facts);

  void setNonNullParamOnNormalExits(DexEncodedMethod method, BitSet facts);

  void classInitializerMayBePostponed(DexEncodedMethod method);
}
