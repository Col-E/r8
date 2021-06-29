// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.inlining.NeverSimpleInliningConstraint;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraint;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.classinliner.constraint.ClassInlinerMethodConstraint;
import com.android.tools.r8.ir.optimize.enums.classification.EnumUnboxerMethodClassification;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.initializer.DefaultInstanceInitializerInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableSet;
import java.util.BitSet;
import java.util.Set;

public class DefaultMethodOptimizationInfo extends MethodOptimizationInfo {

  public static final DefaultMethodOptimizationInfo DEFAULT_INSTANCE =
      new DefaultMethodOptimizationInfo();

  static Set<DexType> UNKNOWN_INITIALIZED_CLASSES_ON_NORMAL_EXIT = ImmutableSet.of();
  static int UNKNOWN_RETURNED_ARGUMENT = -1;
  static boolean UNKNOWN_NEVER_RETURNS_NORMALLY = false;
  static AbstractValue UNKNOWN_ABSTRACT_RETURN_VALUE = UnknownValue.getInstance();
  static TypeElement UNKNOWN_TYPE = null;
  static ClassTypeElement UNKNOWN_CLASS_TYPE = null;
  static boolean UNKNOWN_CHECKS_NULL_RECEIVER_BEFORE_ANY_SIDE_EFFECT = false;
  static boolean UNKNOWN_TRIGGERS_CLASS_INIT_BEFORE_ANY_SIDE_EFFECT = false;
  static boolean UNKNOWN_INITIALIZER_ENABLING_JAVA_ASSERTIONS = false;
  static boolean UNKNOWN_MAY_HAVE_SIDE_EFFECTS = true;
  static boolean UNKNOWN_RETURN_VALUE_ONLY_DEPENDS_ON_ARGUMENTS = false;
  static BitSet NO_NULL_PARAMETER_OR_THROW_FACTS = null;
  static BitSet NO_NULL_PARAMETER_ON_NORMAL_EXITS_FACTS = null;
  static AndroidApiLevel UNKNOWN_API_REFERENCE_LEVEL = null;

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
  public ClassInlinerMethodConstraint getClassInlinerMethodConstraint() {
    return ClassInlinerMethodConstraint.alwaysFalse();
  }

  @Override
  public EnumUnboxerMethodClassification getEnumUnboxerMethodClassification() {
    return EnumUnboxerMethodClassification.unknown();
  }

  @Override
  public TypeElement getDynamicUpperBoundType() {
    return UNKNOWN_TYPE;
  }

  @Override
  public ClassTypeElement getDynamicLowerBoundType() {
    return UNKNOWN_CLASS_TYPE;
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
  public boolean isReachabilitySensitive() {
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
  public boolean isInitializerEnablingJavaVmAssertions() {
    return UNKNOWN_INITIALIZER_ENABLING_JAVA_ASSERTIONS;
  }

  @Override
  public boolean forceInline() {
    return false;
  }

  @Override
  public boolean neverInline() {
    return false;
  }

  @Override
  public boolean checksNullReceiverBeforeAnySideEffect() {
    return UNKNOWN_CHECKS_NULL_RECEIVER_BEFORE_ANY_SIDE_EFFECT;
  }

  @Override
  public boolean triggersClassInitBeforeAnySideEffect() {
    return UNKNOWN_TRIGGERS_CLASS_INIT_BEFORE_ANY_SIDE_EFFECT;
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
  public AndroidApiLevel getApiReferenceLevel(AndroidApiLevel minApi) {
    throw new RuntimeException("Should never be called");
  }

  @Override
  public boolean hasApiReferenceLevel() {
    return false;
  }

  @Override
  public MutableMethodOptimizationInfo toMutableOptimizationInfo() {
    return new MutableMethodOptimizationInfo();
  }
}
