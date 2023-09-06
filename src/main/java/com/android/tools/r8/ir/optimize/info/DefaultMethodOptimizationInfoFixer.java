// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
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

public class DefaultMethodOptimizationInfoFixer extends MethodOptimizationInfoFixer {

  @Override
  public BridgeInfo fixupBridgeInfo(BridgeInfo bridgeInfo) {
    return bridgeInfo;
  }

  @Override
  public CallSiteOptimizationInfo fixupCallSiteOptimizationInfo(
      ConcreteCallSiteOptimizationInfo callSiteOptimizationInfo) {
    return callSiteOptimizationInfo;
  }

  @Override
  public ClassInlinerMethodConstraint fixupClassInlinerMethodConstraint(
      AppView<AppInfoWithLiveness> appView,
      ClassInlinerMethodConstraint classInlinerMethodConstraint) {
    return classInlinerMethodConstraint;
  }

  @Override
  public EnumUnboxerMethodClassification fixupEnumUnboxerMethodClassification(
      EnumUnboxerMethodClassification classification) {
    return classification;
  }

  @Override
  public InstanceInitializerInfoCollection fixupInstanceInitializerInfo(
      AppView<AppInfoWithLiveness> appView,
      InstanceInitializerInfoCollection instanceInitializerInfo) {
    return instanceInitializerInfo;
  }

  @Override
  public BitSet fixupNonNullParamOnNormalExits(BitSet nonNullParamOnNormalExits) {
    return nonNullParamOnNormalExits;
  }

  @Override
  public BitSet fixupNonNullParamOrThrow(BitSet nonNullParamOrThrow) {
    return nonNullParamOrThrow;
  }

  @Override
  public int fixupReturnedArgumentIndex(int returnedArgumentIndex) {
    return returnedArgumentIndex;
  }

  @Override
  public SimpleInliningConstraint fixupSimpleInliningConstraint(
      AppView<AppInfoWithLiveness> appView,
      SimpleInliningConstraint constraint,
      SimpleInliningConstraintFactory factory) {
    return constraint;
  }

  @Override
  public BitSet fixupArguments(BitSet arguments) {
    return arguments;
  }
}
