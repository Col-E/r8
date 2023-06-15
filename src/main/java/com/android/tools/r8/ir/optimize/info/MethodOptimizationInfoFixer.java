// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraint;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraintFactory;
import com.android.tools.r8.ir.optimize.classinliner.constraint.ClassInlinerMethodConstraint;
import com.android.tools.r8.ir.optimize.enums.classification.EnumUnboxerMethodClassification;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfoCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.BitSet;

public abstract class MethodOptimizationInfoFixer {

  public abstract BridgeInfo fixupBridgeInfo(BridgeInfo bridgeInfo);

  public abstract CallSiteOptimizationInfo fixupCallSiteOptimizationInfo(
      ConcreteCallSiteOptimizationInfo callSiteOptimizationInfo);

  public abstract ClassInlinerMethodConstraint fixupClassInlinerMethodConstraint(
      AppView<AppInfoWithLiveness> appView,
      ClassInlinerMethodConstraint classInlinerMethodConstraint);

  public abstract EnumUnboxerMethodClassification fixupEnumUnboxerMethodClassification(
      EnumUnboxerMethodClassification classification);

  public abstract InstanceInitializerInfoCollection fixupInstanceInitializerInfo(
      AppView<AppInfoWithLiveness> appView,
      InstanceInitializerInfoCollection instanceInitializerInfo);

  public abstract BitSet fixupNonNullParamOnNormalExits(BitSet nonNullParamOnNormalExits);

  public abstract BitSet fixupNonNullParamOrThrow(BitSet nonNullParamOrThrow);

  public abstract int fixupReturnedArgumentIndex(int returnedArgumentIndex);

  public abstract SimpleInliningConstraint fixupSimpleInliningConstraint(
      AppView<AppInfoWithLiveness> appView,
      SimpleInliningConstraint constraint,
      SimpleInliningConstraintFactory factory);

  public abstract BitSet fixupArguments(BitSet arguments);
}
