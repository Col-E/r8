// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import static com.android.tools.r8.utils.OptionalBool.UNKNOWN;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraint;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
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
    NeverInline,
    ForceInline,
    Default
  }

  public abstract boolean cannotBeKept();

  public abstract boolean classInitializerMayBePostponed();

  public abstract ClassInlinerMethodConstraint getClassInlinerMethodConstraint();

  public abstract EnumUnboxerMethodClassification getEnumUnboxerMethodClassification();

  public abstract TypeElement getDynamicUpperBoundType();

  public final TypeElement getDynamicUpperBoundTypeOrElse(TypeElement orElse) {
    TypeElement dynamicUpperBoundType = getDynamicUpperBoundType();
    return dynamicUpperBoundType != null ? dynamicUpperBoundType : orElse;
  }

  public abstract ClassTypeElement getDynamicLowerBoundType();

  public final boolean hasNonNullParamOrThrow() {
    return getNonNullParamOrThrow() != null;
  }

  public abstract BitSet getNonNullParamOrThrow();

  public final boolean hasNonNullParamOnNormalExits() {
    return getNonNullParamOnNormalExits() != null;
  }

  public abstract BitSet getNonNullParamOnNormalExits();

  public abstract boolean hasBeenInlinedIntoSingleCallSite();

  public abstract boolean isReachabilitySensitive();

  public abstract boolean returnsArgument();

  public abstract int getReturnedArgument();

  public abstract boolean neverReturnsNormally();

  public abstract BridgeInfo getBridgeInfo();

  public abstract Set<DexType> getInitializedClassesOnNormalExit();

  public abstract InstanceInitializerInfo getContextInsensitiveInstanceInitializerInfo();

  public abstract InstanceInitializerInfo getInstanceInitializerInfo(InvokeDirect invoke);

  public abstract boolean isInitializerEnablingJavaVmAssertions();

  public abstract AbstractValue getAbstractReturnValue();

  public abstract SimpleInliningConstraint getNopInliningConstraint(InternalOptions options);

  public abstract SimpleInliningConstraint getSimpleInliningConstraint();

  public abstract boolean forceInline();

  public abstract boolean neverInline();

  public abstract boolean checksNullReceiverBeforeAnySideEffect();

  public abstract boolean triggersClassInitBeforeAnySideEffect();

  public abstract boolean mayHaveSideEffects();

  /** Context sensitive version of {@link #mayHaveSideEffects()}. */
  public abstract boolean mayHaveSideEffects(InvokeMethod invoke, InternalOptions options);

  public abstract boolean returnValueOnlyDependsOnArguments();

  public abstract boolean returnValueHasBeenPropagated();

  public static OptionalBool isApiSafeForInlining(
      MethodOptimizationInfo caller, MethodOptimizationInfo inlinee, InternalOptions options) {
    if (!options.apiModelingOptions().enableApiCallerIdentification) {
      return OptionalBool.TRUE;
    }
    if (!caller.hasApiReferenceLevel() || !inlinee.hasApiReferenceLevel()) {
      return UNKNOWN;
    }
    return OptionalBool.of(
        caller
            .getApiReferenceLevel(options.minApiLevel)
            .isGreaterThanOrEqualTo(inlinee.getApiReferenceLevel(options.minApiLevel)));
  }
}
