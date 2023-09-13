// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

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

public abstract class MethodOptimizationInfo
    implements MemberOptimizationInfo<MutableMethodOptimizationInfo> {

  enum InlinePreference {
    MultiCallerInline,
    ForceInline,
    Default
  }

  public boolean isDefault() {
    return false;
  }

  public abstract boolean cannotBeKept();

  public abstract boolean classInitializerMayBePostponed();

  public abstract CallSiteOptimizationInfo getArgumentInfos();

  public abstract ClassInlinerMethodConstraint getClassInlinerMethodConstraint();

  public abstract EnumUnboxerMethodClassification getEnumUnboxerMethodClassification();

  public abstract DynamicType getDynamicType();

  public abstract int getMaxRemovedAndroidLogLevel();

  public final boolean hasNonNullParamOrThrow() {
    return getNonNullParamOrThrow() != null;
  }

  public abstract BitSet getNonNullParamOrThrow();

  public final boolean hasNonNullParamOnNormalExits() {
    return getNonNullParamOnNormalExits() != null;
  }

  public abstract BitSet getNonNullParamOnNormalExits();

  public abstract boolean hasBeenInlinedIntoSingleCallSite();

  public abstract boolean returnsArgument();

  public abstract int getReturnedArgument();

  public abstract boolean neverReturnsNormally();

  public abstract BridgeInfo getBridgeInfo();

  public abstract Set<DexType> getInitializedClassesOnNormalExit();

  public abstract InstanceInitializerInfo getContextInsensitiveInstanceInitializerInfo();

  public abstract InstanceInitializerInfo getInstanceInitializerInfo(InvokeDirect invoke);

  public abstract boolean isConvertCheckNotNull();

  public abstract boolean isInitializerEnablingJavaVmAssertions();

  public abstract AbstractValue getAbstractReturnValue();

  public abstract SimpleInliningConstraint getNopInliningConstraint(InternalOptions options);

  public abstract SimpleInliningConstraint getSimpleInliningConstraint();

  public abstract boolean hasParametersWithBitwiseOperations();

  public abstract BitSet getParametersWithBitwiseOperations();

  public final boolean hasUnusedArguments() {
    assert getUnusedArguments() == null || !getUnusedArguments().isEmpty();
    return getUnusedArguments() != null;
  }

  public abstract BitSet getUnusedArguments();

  public abstract boolean isMultiCallerMethod();

  public abstract OptionalBool isReturnValueUsed();

  public abstract boolean forceInline();

  public abstract boolean mayHaveSideEffects();

  /** Context sensitive version of {@link #mayHaveSideEffects()}. */
  public abstract boolean mayHaveSideEffects(InvokeMethod invoke, InternalOptions options);

  public abstract boolean returnValueOnlyDependsOnArguments();

  public abstract boolean returnValueHasBeenPropagated();

  @Override
  public boolean isMethodOptimizationInfo() {
    return true;
  }

  @Override
  public MethodOptimizationInfo asMethodOptimizationInfo() {
    return this;
  }
}
