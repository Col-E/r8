// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.proto.ArgumentInfo;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraint;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraintFactory;
import com.android.tools.r8.ir.optimize.classinliner.constraint.ClassInlinerMethodConstraint;
import com.android.tools.r8.ir.optimize.enums.classification.EnumUnboxerMethodClassification;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.ConcreteCallSiteOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfoFixer;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfoCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.BitSet;

public class RewrittenPrototypeDescriptionMethodOptimizationInfoFixer
    extends MethodOptimizationInfoFixer {

  private final RewrittenPrototypeDescription prototypeChanges;

  public RewrittenPrototypeDescriptionMethodOptimizationInfoFixer(
      RewrittenPrototypeDescription prototypeChanges) {
    this.prototypeChanges = prototypeChanges;
  }

  private ArgumentInfoCollection getArgumentInfoCollection() {
    return prototypeChanges.getArgumentInfoCollection();
  }

  /**
   * Function for rewriting the bridge info on a piece of method optimization info after prototype
   * changes were made.
   */
  @Override
  public BridgeInfo fixupBridgeInfo(BridgeInfo bridgeInfo) {
    if (getArgumentInfoCollection().isEmpty()) {
      return bridgeInfo;
    }
    return null;
  }

  /**
   * Function for rewriting the call site optimization info on a method after prototype changes were
   * made.
   */
  @Override
  public CallSiteOptimizationInfo fixupCallSiteOptimizationInfo(
      ConcreteCallSiteOptimizationInfo callSiteOptimizationInfo) {
    if (prototypeChanges.isEmpty()) {
      return callSiteOptimizationInfo;
    }
    return callSiteOptimizationInfo.fixupAfterParametersChanged(prototypeChanges);
  }

  /**
   * Function for rewriting the class inliner method constraint on a piece of method optimization
   * info after prototype changes were made.
   */
  @Override
  public ClassInlinerMethodConstraint fixupClassInlinerMethodConstraint(
      AppView<AppInfoWithLiveness> appView,
      ClassInlinerMethodConstraint classInlinerMethodConstraint) {
    if (getArgumentInfoCollection().isEmpty()) {
      return classInlinerMethodConstraint;
    }
    return classInlinerMethodConstraint.fixupAfterParametersChanged(
        appView, getArgumentInfoCollection());
  }

  /**
   * Function for rewriting the enum unboxer method classification on a piece of method optimization
   * info after prototype changes were made.
   */
  @Override
  public EnumUnboxerMethodClassification fixupEnumUnboxerMethodClassification(
      EnumUnboxerMethodClassification classification) {
    if (getArgumentInfoCollection().isEmpty()) {
      return classification;
    }
    return classification.fixupAfterParametersChanged(getArgumentInfoCollection());
  }

  /**
   * Function for rewriting the instance initializer information on a piece of method optimization
   * info after prototype changes were made.
   */
  @Override
  public InstanceInitializerInfoCollection fixupInstanceInitializerInfo(
      AppView<AppInfoWithLiveness> appView,
      InstanceInitializerInfoCollection instanceInitializerInfo) {
    if (getArgumentInfoCollection().isEmpty()) {
      return instanceInitializerInfo;
    }
    return instanceInitializerInfo.fixupAfterParametersChanged(
        appView, getArgumentInfoCollection());
  }

  /**
   * Function for rewriting the non-null-param-on-normal-exits information on a piece of method
   * optimization info after prototype changes were made.
   */
  @Override
  public BitSet fixupNonNullParamOnNormalExits(BitSet nonNullParamOnNormalExits) {
    return fixupArgumentInfo(nonNullParamOnNormalExits);
  }

  /**
   * Function for rewriting the non-null-param-or-throw information on a piece of method
   * optimization info after prototype changes were made.
   */
  @Override
  public BitSet fixupNonNullParamOrThrow(BitSet nonNullParamOrThrow) {
    return fixupArgumentInfo(nonNullParamOrThrow);
  }

  /**
   * Function for rewriting the returned argument information on a piece of method optimization info
   * after prototype changes were made.
   */
  @Override
  public int fixupReturnedArgumentIndex(int returnedArgumentIndex) {
    if (getArgumentInfoCollection().isEmpty() || returnedArgumentIndex < 0) {
      return returnedArgumentIndex;
    }
    return getArgumentInfoCollection().isArgumentRemoved(returnedArgumentIndex)
        ? -1
        : getArgumentInfoCollection().getNewArgumentIndex(returnedArgumentIndex);
  }

  /**
   * Function for rewriting the simple inlining constraint on a piece of method optimization info
   * after prototype changes were made.
   */
  @Override
  public SimpleInliningConstraint fixupSimpleInliningConstraint(
      AppView<AppInfoWithLiveness> appView,
      SimpleInliningConstraint constraint,
      SimpleInliningConstraintFactory factory) {
    if (getArgumentInfoCollection().isEmpty()) {
      return constraint;
    }
    return constraint.fixupAfterParametersChanged(appView, getArgumentInfoCollection(), factory);
  }

  /**
   * Function for rewriting a BitSet that stores a bit per argument on a piece of method
   * optimization info after prototype changes were made.
   */
  @Override
  public BitSet fixupArguments(BitSet arguments) {
    return fixupArgumentInfo(arguments);
  }

  private BitSet fixupArgumentInfo(BitSet bitSet) {
    if (getArgumentInfoCollection().isEmpty() || bitSet == null) {
      return bitSet;
    }
    int n = bitSet.length();
    BitSet rewrittenBitSet = new BitSet(n);
    for (int argumentIndex = 0; argumentIndex < n; argumentIndex++) {
      if (!bitSet.get(argumentIndex)) {
        continue;
      }
      ArgumentInfo argumentInfo = getArgumentInfoCollection().getArgumentInfo(argumentIndex);
      if (argumentInfo.isRemovedArgumentInfo() || argumentInfo.isRewrittenTypeInfo()) {
        continue;
      }
      rewrittenBitSet.set(getArgumentInfoCollection().getNewArgumentIndex(argumentIndex));
    }
    return rewrittenBitSet.isEmpty() ? null : rewrittenBitSet;
  }
}
