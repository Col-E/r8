// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.inlining.NeverSimpleInliningConstraint;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraint;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.classinliner.constraint.ClassInlinerMethodConstraint;
import com.android.tools.r8.ir.optimize.enums.classification.EnumUnboxerMethodClassification;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.initializer.DefaultInstanceInitializerInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.android.tools.r8.shaking.MaximumRemovedAndroidLogLevelRule;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OptionalBool;
import com.google.common.collect.ImmutableSet;
import java.util.BitSet;
import java.util.Set;

public class DefaultMethodOptimizationInfo extends MethodOptimizationInfo {

  public static final DefaultMethodOptimizationInfo DEFAULT_INSTANCE =
      new DefaultMethodOptimizationInfo();

  static final Set<DexType> UNKNOWN_INITIALIZED_CLASSES_ON_NORMAL_EXIT = ImmutableSet.of();
  static final int UNKNOWN_RETURNED_ARGUMENT = -1;
  static final boolean UNKNOWN_NEVER_RETURNS_NORMALLY = false;
  static final AbstractValue UNKNOWN_ABSTRACT_RETURN_VALUE = UnknownValue.getInstance();
  static final boolean UNKNOWN_TRIGGERS_CLASS_INIT_BEFORE_ANY_SIDE_EFFECT = false;
  static final boolean UNKNOWN_INITIALIZER_ENABLING_JAVA_ASSERTIONS = false;
  static final boolean UNKNOWN_MAY_HAVE_SIDE_EFFECTS = true;
  static final boolean UNKNOWN_RETURN_VALUE_ONLY_DEPENDS_ON_ARGUMENTS = false;
  static final BitSet NO_NULL_PARAMETER_OR_THROW_FACTS = null;
  static final BitSet NO_NULL_PARAMETER_ON_NORMAL_EXITS_FACTS = null;

  protected DefaultMethodOptimizationInfo() {}

  public static DefaultMethodOptimizationInfo getInstance() {
    return DEFAULT_INSTANCE;
  }

  @Override
  public boolean cannotBeKept() {
    return false;
  }

  @Override
  public boolean classInitializerMayBePostponed() {
    return false;
  }

  @Override
  public CallSiteOptimizationInfo getArgumentInfos() {
    return CallSiteOptimizationInfo.top();
  }

  @Override
  public ClassInlinerMethodConstraint getClassInlinerMethodConstraint() {
    return ClassInlinerMethodConstraint.alwaysFalse();
  }

  @Override
  public EnumUnboxerMethodClassification getEnumUnboxerMethodClassification() {
    return EnumUnboxerMethodClassification.unknown();
  }

  @Override
  public DynamicType getDynamicType() {
    return DynamicType.unknown();
  }

  @Override
  public Set<DexType> getInitializedClassesOnNormalExit() {
    return UNKNOWN_INITIALIZED_CLASSES_ON_NORMAL_EXIT;
  }

  @Override
  public InstanceInitializerInfo getContextInsensitiveInstanceInitializerInfo() {
    return DefaultInstanceInitializerInfo.getInstance();
  }

  @Override
  public InstanceInitializerInfo getInstanceInitializerInfo(InvokeDirect invoke) {
    return DefaultInstanceInitializerInfo.getInstance();
  }

  @Override
  public int getMaxRemovedAndroidLogLevel() {
    return MaximumRemovedAndroidLogLevelRule.NOT_SET;
  }

  @Override
  public BitSet getNonNullParamOrThrow() {
    return NO_NULL_PARAMETER_OR_THROW_FACTS;
  }

  @Override
  public BitSet getNonNullParamOnNormalExits() {
    return NO_NULL_PARAMETER_ON_NORMAL_EXITS_FACTS;
  }

  @Override
  public boolean hasBeenInlinedIntoSingleCallSite() {
    return false;
  }

  @Override
  public boolean returnsArgument() {
    return false;
  }

  @Override
  public int getReturnedArgument() {
    assert returnsArgument();
    return UNKNOWN_RETURNED_ARGUMENT;
  }

  @Override
  public boolean neverReturnsNormally() {
    return UNKNOWN_NEVER_RETURNS_NORMALLY;
  }

  @Override
  public BridgeInfo getBridgeInfo() {
    return null;
  }

  @Override
  public AbstractValue getAbstractReturnValue() {
    return UNKNOWN_ABSTRACT_RETURN_VALUE;
  }

  @Override
  public SimpleInliningConstraint getNopInliningConstraint(InternalOptions options) {
    return NeverSimpleInliningConstraint.getInstance();
  }

  @Override
  public SimpleInliningConstraint getSimpleInliningConstraint() {
    return NeverSimpleInliningConstraint.getInstance();
  }

  @Override
  public boolean hasParametersWithBitwiseOperations() {
    return false;
  }

  @Override
  public BitSet getParametersWithBitwiseOperations() {
    return null;
  }

  @Override
  public BitSet getUnusedArguments() {
    return null;
  }

  @Override
  public boolean isConvertCheckNotNull() {
    return false;
  }

  @Override
  public boolean isInitializerEnablingJavaVmAssertions() {
    return UNKNOWN_INITIALIZER_ENABLING_JAVA_ASSERTIONS;
  }

  @Override
  public boolean isMultiCallerMethod() {
    return false;
  }

  @Override
  public OptionalBool isReturnValueUsed() {
    return OptionalBool.unknown();
  }

  @Override
  public boolean forceInline() {
    return false;
  }

  @Override
  public boolean mayHaveSideEffects() {
    return UNKNOWN_MAY_HAVE_SIDE_EFFECTS;
  }

  @Override
  public boolean mayHaveSideEffects(InvokeMethod invoke, InternalOptions options) {
    return UNKNOWN_MAY_HAVE_SIDE_EFFECTS;
  }

  @Override
  public boolean returnValueOnlyDependsOnArguments() {
    return UNKNOWN_RETURN_VALUE_ONLY_DEPENDS_ON_ARGUMENTS;
  }

  @Override
  public boolean returnValueHasBeenPropagated() {
    return false;
  }

  @Override
  public MutableMethodOptimizationInfo toMutableOptimizationInfo() {
    return new MutableMethodOptimizationInfo();
  }
}
