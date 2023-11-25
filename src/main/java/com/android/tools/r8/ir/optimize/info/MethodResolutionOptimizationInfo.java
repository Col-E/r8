// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraint;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.classinliner.constraint.ClassInlinerMethodConstraint;
import com.android.tools.r8.ir.optimize.enums.classification.EnumUnboxerMethodClassification;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OptionalBool;
import java.util.BitSet;
import java.util.Set;
import java.util.function.Consumer;

public class MethodResolutionOptimizationInfo extends MethodOptimizationInfo {

  private final AbstractValue abstractReturnValue;
  private final DynamicType dynamicReturnType;
  private final boolean mayHaveSideEffects;
  private final boolean neverReturnsNormally;
  private final BitSet nonNullParamOnNormalExits;
  private final BitSet nonNullParamOrThrow;
  private final int returnedArgument;
  private final boolean returnValueOnlyDependsOnArguments;

  MethodResolutionOptimizationInfo(
      AbstractValue abstractReturnValue,
      DynamicType dynamicReturnType,
      boolean mayHaveSideEffects,
      boolean neverReturnsNormally,
      BitSet nonNullParamOnNormalExits,
      BitSet nonNullParamOrThrow,
      int returnedArgument,
      boolean returnValueOnlyDependsOnArguments) {
    this.abstractReturnValue = abstractReturnValue;
    this.dynamicReturnType = dynamicReturnType;
    this.mayHaveSideEffects = mayHaveSideEffects;
    this.neverReturnsNormally = neverReturnsNormally;
    this.nonNullParamOnNormalExits = nonNullParamOnNormalExits;
    this.nonNullParamOrThrow = nonNullParamOrThrow;
    this.returnedArgument = returnedArgument;
    this.returnValueOnlyDependsOnArguments = returnValueOnlyDependsOnArguments;
  }

  static Builder builder() {
    return new Builder();
  }

  static MethodOptimizationInfo create(MethodOptimizationInfo optimizationInfo) {
    return builder()
        .setAbstractReturnValue(optimizationInfo.getAbstractReturnValue())
        .setDynamicReturnType(optimizationInfo.getDynamicType())
        .setMayHaveSideEffects(optimizationInfo.mayHaveSideEffects())
        .setNeverReturnsNormally(optimizationInfo.neverReturnsNormally())
        .setNonNullParamOnNormalExits(optimizationInfo.getNonNullParamOnNormalExits())
        .setNonNullParamOrThrow(optimizationInfo.getNonNullParamOrThrow())
        .applyIf(
            optimizationInfo.returnsArgument(),
            builder -> builder.setReturnedArgument(optimizationInfo.getReturnedArgument()))
        .setReturnValueOnlyDependsOnArguments(optimizationInfo.returnValueOnlyDependsOnArguments())
        .build();
  }

  @Override
  public boolean isMethodResolutionOptimizationInfo() {
    return true;
  }

  @Override
  public AbstractValue getAbstractReturnValue() {
    return abstractReturnValue;
  }

  @Override
  public DynamicType getDynamicType() {
    return dynamicReturnType;
  }

  @Override
  public boolean mayHaveSideEffects() {
    return mayHaveSideEffects;
  }

  @Override
  public boolean mayHaveSideEffects(InvokeMethod invoke, InternalOptions options) {
    return mayHaveSideEffects;
  }

  @Override
  public boolean neverReturnsNormally() {
    return neverReturnsNormally;
  }

  @Override
  public BitSet getNonNullParamOnNormalExits() {
    return nonNullParamOnNormalExits;
  }

  @Override
  public BitSet getNonNullParamOrThrow() {
    return nonNullParamOrThrow;
  }

  @Override
  public boolean returnsArgument() {
    return returnedArgument >= 0;
  }

  @Override
  public int getReturnedArgument() {
    return returnedArgument;
  }

  @Override
  public boolean returnValueOnlyDependsOnArguments() {
    return returnValueOnlyDependsOnArguments;
  }

  @Override
  public boolean hasBeenInlinedIntoSingleCallSite() {
    throw new Unreachable();
  }

  @Override
  public MutableMethodOptimizationInfo toMutableOptimizationInfo() {
    throw new Unreachable();
  }

  @Override
  public boolean cannotBeKept() {
    throw new Unreachable();
  }

  @Override
  public boolean classInitializerMayBePostponed() {
    throw new Unreachable();
  }

  @Override
  public CallSiteOptimizationInfo getArgumentInfos() {
    throw new Unreachable();
  }

  @Override
  public ClassInlinerMethodConstraint getClassInlinerMethodConstraint() {
    throw new Unreachable();
  }

  @Override
  public EnumUnboxerMethodClassification getEnumUnboxerMethodClassification() {
    throw new Unreachable();
  }

  @Override
  public int getMaxRemovedAndroidLogLevel() {
    throw new Unreachable();
  }

  @Override
  public BridgeInfo getBridgeInfo() {
    throw new Unreachable();
  }

  @Override
  public Set<DexType> getInitializedClassesOnNormalExit() {
    throw new Unreachable();
  }

  @Override
  public InstanceInitializerInfo getContextInsensitiveInstanceInitializerInfo() {
    throw new Unreachable();
  }

  @Override
  public InstanceInitializerInfo getInstanceInitializerInfo(InvokeDirect invoke) {
    throw new Unreachable();
  }

  @Override
  public boolean isConvertCheckNotNull() {
    throw new Unreachable();
  }

  @Override
  public boolean isInitializerEnablingJavaVmAssertions() {
    throw new Unreachable();
  }

  @Override
  public SimpleInliningConstraint getNopInliningConstraint(InternalOptions options) {
    throw new Unreachable();
  }

  @Override
  public SimpleInliningConstraint getSimpleInliningConstraint() {
    throw new Unreachable();
  }

  @Override
  public boolean hasParametersWithBitwiseOperations() {
    throw new Unreachable();
  }

  @Override
  public BitSet getParametersWithBitwiseOperations() {
    throw new Unreachable();
  }

  @Override
  public BitSet getUnusedArguments() {
    throw new Unreachable();
  }

  @Override
  public boolean isMultiCallerMethod() {
    throw new Unreachable();
  }

  @Override
  public OptionalBool isReturnValueUsed() {
    throw new Unreachable();
  }

  @Override
  public boolean forceInline() {
    throw new Unreachable();
  }

  @Override
  public boolean returnValueHasBeenPropagated() {
    throw new Unreachable();
  }

  static class Builder {

    private AbstractValue abstractReturnValue = AbstractValue.unknown();
    private DynamicType dynamicReturnType = DynamicType.unknown();
    private boolean mayHaveSideEffects = true;
    private boolean neverReturnsNormally = false;
    private BitSet nonNullParamOnNormalExits = null;
    private BitSet nonNullParamOrThrow = null;
    private int returnedArgument = -1;
    private boolean returnValueOnlyDependsOnArguments = false;

    Builder applyIf(boolean condition, Consumer<Builder> fn) {
      if (condition) {
        fn.accept(this);
      }
      return this;
    }

    boolean isEffectivelyDefault() {
      return dynamicReturnType.isUnknown()
          && abstractReturnValue.isUnknown()
          && returnedArgument < 0
          && nonNullParamOnNormalExits == null
          && nonNullParamOrThrow == null
          && mayHaveSideEffects
          && !neverReturnsNormally
          && !returnValueOnlyDependsOnArguments;
    }

    Builder setAbstractReturnValue(AbstractValue abstractReturnValue) {
      this.abstractReturnValue = abstractReturnValue;
      return this;
    }

    Builder setDynamicReturnType(DynamicType dynamicReturnType) {
      this.dynamicReturnType = dynamicReturnType;
      return this;
    }

    Builder setMayHaveSideEffects(boolean mayHaveSideEffects) {
      this.mayHaveSideEffects = mayHaveSideEffects;
      return this;
    }

    Builder setNeverReturnsNormally(boolean neverReturnsNormally) {
      this.neverReturnsNormally = neverReturnsNormally;
      return this;
    }

    Builder setNonNullParamOnNormalExits(BitSet nonNullParamOnNormalExits) {
      this.nonNullParamOnNormalExits = nonNullParamOnNormalExits;
      return this;
    }

    Builder setNonNullParamOrThrow(BitSet nonNullParamOrThrow) {
      this.nonNullParamOrThrow = nonNullParamOrThrow;
      return this;
    }

    Builder setReturnedArgument(int returnedArgument) {
      this.returnedArgument = returnedArgument;
      return this;
    }

    Builder setReturnValueOnlyDependsOnArguments(boolean returnValueOnlyDependsOnArguments) {
      this.returnValueOnlyDependsOnArguments = returnValueOnlyDependsOnArguments;
      return this;
    }

    MethodOptimizationInfo build() {
      if (isEffectivelyDefault()) {
        return DefaultMethodOptimizationInfo.getInstance();
      }
      return new MethodResolutionOptimizationInfo(
          abstractReturnValue,
          dynamicReturnType,
          mayHaveSideEffects,
          neverReturnsNormally,
          nonNullParamOnNormalExits,
          nonNullParamOrThrow,
          returnedArgument,
          returnValueOnlyDependsOnArguments);
    }
  }
}
