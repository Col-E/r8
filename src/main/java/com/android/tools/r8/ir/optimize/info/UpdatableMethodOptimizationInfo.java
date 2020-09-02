// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.optimize.classinliner.ClassInlinerEligibilityInfo;
import com.android.tools.r8.ir.optimize.info.ParameterUsagesInfo.ParameterUsage;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.initializer.DefaultInstanceInitializerInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.BitSet;
import java.util.Set;
import java.util.function.Function;

public class UpdatableMethodOptimizationInfo extends MethodOptimizationInfo {

  private Set<DexType> initializedClassesOnNormalExit =
      DefaultMethodOptimizationInfo.UNKNOWN_INITIALIZED_CLASSES_ON_NORMAL_EXIT;
  private int returnedArgument = DefaultMethodOptimizationInfo.UNKNOWN_RETURNED_ARGUMENT;
  private AbstractValue abstractReturnValue =
      DefaultMethodOptimizationInfo.UNKNOWN_ABSTRACT_RETURN_VALUE;
  private TypeElement returnsObjectWithUpperBoundType = DefaultMethodOptimizationInfo.UNKNOWN_TYPE;
  private ClassTypeElement returnsObjectWithLowerBoundType =
      DefaultMethodOptimizationInfo.UNKNOWN_CLASS_TYPE;
  private InlinePreference inlining = InlinePreference.Default;
  // Stores information about instance methods and constructors for
  // class inliner, null value indicates that the method is not eligible.
  private BridgeInfo bridgeInfo = null;
  private ClassInlinerEligibilityInfo classInlinerEligibility =
      DefaultMethodOptimizationInfo.UNKNOWN_CLASS_INLINER_ELIGIBILITY;
  private InstanceInitializerInfo instanceInitializerInfo =
      DefaultInstanceInitializerInfo.getInstance();
  private ParameterUsagesInfo parametersUsages =
      DefaultMethodOptimizationInfo.UNKNOWN_PARAMETER_USAGE_INFO;
  // Stores information about nullability hint per parameter. If set, that means, the method
  // somehow (e.g., null check, such as arg != null, or using checkParameterIsNotNull) ensures
  // the corresponding parameter is not null, or throws NPE before any other side effects.
  // This info is used by {@link UninstantiatedTypeOptimization#rewriteInvoke} that replaces an
  // invocation with null throwing code if an always-null argument is passed. Also used by Inliner
  // to give a credit to null-safe code, e.g., Kotlin's null safe argument.
  // Note that this bit set takes into account the receiver for instance methods.
  private BitSet nonNullParamOrThrow =
      DefaultMethodOptimizationInfo.NO_NULL_PARAMETER_OR_THROW_FACTS;
  // Stores information about nullability facts per parameter. If set, that means, the method
  // somehow (e.g., null check, such as arg != null, or NPE-throwing instructions such as array
  // access or another invocation) ensures the corresponding parameter is not null, and that is
  // guaranteed until the normal exits. That is, if the invocation of this method is finished
  // normally, the recorded parameter is definitely not null. These facts are used to propagate
  // non-null information through {@link NonNullTracker}.
  // Note that this bit set takes into account the receiver for instance methods.
  private BitSet nonNullParamOnNormalExits =
      DefaultMethodOptimizationInfo.NO_NULL_PARAMETER_ON_NORMAL_EXITS_FACTS;

  // To reduce the memory footprint of UpdatableMethodOptimizationInfo, all the boolean fields are
  // merged into a flag int field. The various static final FLAG fields indicate which bit is
  // used by each boolean. DEFAULT_FLAGS encodes the default value for efficient instantiation and
  // is computed during class initialization from the default method optimization info. The
  // methods setFlag, clearFlag and isFlagSet are used to access the booleans.
  private static final int CANNOT_BE_KEPT_FLAG = 0x1;
  private static final int CLASS_INITIALIZER_MAY_BE_POSTPONED_FLAG = 0x2;
  private static final int HAS_BEEN_INLINED_INTO_SINGLE_CALL_SITE_FLAG = 0x4;
  private static final int MAY_HAVE_SIDE_EFFECT_FLAG = 0x8;
  private static final int RETURN_VALUE_ONLY_DEPENDS_ON_ARGUMENTS_FLAG = 0x10;
  private static final int UNUSED_FLAG_1 = 0x20;
  private static final int NEVER_RETURNS_NORMALLY_FLAG = 0x40;
  private static final int UNUSED_FLAG_2 = 0x80;
  private static final int CHECKS_NULL_RECEIVER_BEFORE_ANY_SIDE_EFFECT_FLAG = 0x100;
  private static final int TRIGGERS_CLASS_INIT_BEFORE_ANY_SIDE_EFFECT_FLAG = 0x200;
  private static final int INITIALIZER_ENABLING_JAVA_ASSERTIONS_FLAG = 0x400;
  private static final int REACHABILITY_SENSITIVE_FLAG = 0x800;
  private static final int RETURN_VALUE_HAS_BEEN_PROPAGATED_FLAG = 0x1000;

  private static final int DEFAULT_FLAGS;

  static {
    int defaultFlags = 0;
    MethodOptimizationInfo defaultOptInfo = DefaultMethodOptimizationInfo.DEFAULT_INSTANCE;
    defaultFlags |= BooleanUtils.intValue(defaultOptInfo.cannotBeKept()) * CANNOT_BE_KEPT_FLAG;
    defaultFlags |=
        BooleanUtils.intValue(defaultOptInfo.classInitializerMayBePostponed())
            * CLASS_INITIALIZER_MAY_BE_POSTPONED_FLAG;
    defaultFlags |=
        BooleanUtils.intValue(defaultOptInfo.hasBeenInlinedIntoSingleCallSite())
            * HAS_BEEN_INLINED_INTO_SINGLE_CALL_SITE_FLAG;
    defaultFlags |=
        BooleanUtils.intValue(defaultOptInfo.mayHaveSideEffects()) * MAY_HAVE_SIDE_EFFECT_FLAG;
    defaultFlags |=
        BooleanUtils.intValue(defaultOptInfo.returnValueOnlyDependsOnArguments())
            * RETURN_VALUE_ONLY_DEPENDS_ON_ARGUMENTS_FLAG;
    defaultFlags |= 0 * UNUSED_FLAG_1;
    defaultFlags |=
        BooleanUtils.intValue(defaultOptInfo.neverReturnsNormally()) * NEVER_RETURNS_NORMALLY_FLAG;
    defaultFlags |= 0 * UNUSED_FLAG_2;
    defaultFlags |=
        BooleanUtils.intValue(defaultOptInfo.checksNullReceiverBeforeAnySideEffect())
            * CHECKS_NULL_RECEIVER_BEFORE_ANY_SIDE_EFFECT_FLAG;
    defaultFlags |=
        BooleanUtils.intValue(defaultOptInfo.triggersClassInitBeforeAnySideEffect())
            * TRIGGERS_CLASS_INIT_BEFORE_ANY_SIDE_EFFECT_FLAG;
    defaultFlags |=
        BooleanUtils.intValue(defaultOptInfo.isInitializerEnablingJavaVmAssertions())
            * INITIALIZER_ENABLING_JAVA_ASSERTIONS_FLAG;
    defaultFlags |=
        BooleanUtils.intValue(defaultOptInfo.isReachabilitySensitive())
            * REACHABILITY_SENSITIVE_FLAG;
    defaultFlags |=
        BooleanUtils.intValue(defaultOptInfo.returnValueHasBeenPropagated())
            * RETURN_VALUE_HAS_BEEN_PROPAGATED_FLAG;
    DEFAULT_FLAGS = defaultFlags;
  }

  private int flags = DEFAULT_FLAGS;

  UpdatableMethodOptimizationInfo() {
    // Intentionally left empty, just use the default values.
  }

  // Copy constructor used to create a mutable copy. Do not forget to copy from template when a new
  // field is added.
  private UpdatableMethodOptimizationInfo(UpdatableMethodOptimizationInfo template) {
    flags = template.flags;
    initializedClassesOnNormalExit = template.initializedClassesOnNormalExit;
    returnedArgument = template.returnedArgument;
    abstractReturnValue = template.abstractReturnValue;
    returnsObjectWithUpperBoundType = template.returnsObjectWithUpperBoundType;
    returnsObjectWithLowerBoundType = template.returnsObjectWithLowerBoundType;
    inlining = template.inlining;
    bridgeInfo = template.bridgeInfo;
    classInlinerEligibility = template.classInlinerEligibility;
    instanceInitializerInfo = template.instanceInitializerInfo;
    parametersUsages = template.parametersUsages;
    nonNullParamOrThrow = template.nonNullParamOrThrow;
    nonNullParamOnNormalExits = template.nonNullParamOnNormalExits;
  }

  public UpdatableMethodOptimizationInfo fixupClassTypeReferences(
      Function<DexType, DexType> mapping, AppView<? extends AppInfoWithClassHierarchy> appView) {
    if (returnsObjectWithUpperBoundType != null) {
      returnsObjectWithUpperBoundType =
          returnsObjectWithUpperBoundType.fixupClassTypeReferences(mapping, appView);
    }
    if (returnsObjectWithLowerBoundType != null) {
      TypeElement returnsObjectWithLowerBoundType =
          this.returnsObjectWithLowerBoundType.fixupClassTypeReferences(mapping, appView);
      if (returnsObjectWithLowerBoundType.isClassType()) {
        this.returnsObjectWithLowerBoundType = returnsObjectWithLowerBoundType.asClassType();
      } else {
        assert returnsObjectWithLowerBoundType.isPrimitiveType();
        this.returnsObjectWithUpperBoundType = DefaultMethodOptimizationInfo.UNKNOWN_TYPE;
        this.returnsObjectWithLowerBoundType = DefaultMethodOptimizationInfo.UNKNOWN_CLASS_TYPE;
      }
    }
    return this;
  }

  public UpdatableMethodOptimizationInfo fixupAbstractReturnValue(
      AppView<AppInfoWithLiveness> appView, GraphLens lens) {
    abstractReturnValue = abstractReturnValue.rewrittenWithLens(appView, lens);
    return this;
  }

  public UpdatableMethodOptimizationInfo fixupInstanceInitializerInfo(
      AppView<AppInfoWithLiveness> appView, GraphLens lens) {
    if (instanceInitializerInfo != null) {
      instanceInitializerInfo = instanceInitializerInfo.rewrittenWithLens(appView, lens);
    }
    return this;
  }

  private void setFlag(int flag, boolean value) {
    if (value) {
      setFlag(flag);
    } else {
      clearFlag(flag);
    }
  }

  private void setFlag(int flag) {
    flags |= flag;
  }

  private void clearFlag(int flag) {
    flags &= ~flag;
  }

  private boolean isFlagSet(int flag) {
    return (flags & flag) != 0;
  }

  @Override
  public boolean isDefaultMethodOptimizationInfo() {
    return false;
  }

  @Override
  public boolean isUpdatableMethodOptimizationInfo() {
    return true;
  }

  @Override
  public UpdatableMethodOptimizationInfo asUpdatableMethodOptimizationInfo() {
    return this;
  }

  @Override
  public boolean cannotBeKept() {
    return isFlagSet(CANNOT_BE_KEPT_FLAG);
  }

  // TODO(b/140214568): Should be package-private.
  public void markCannotBeKept() {
    setFlag(CANNOT_BE_KEPT_FLAG);
  }

  @Override
  public boolean classInitializerMayBePostponed() {
    return isFlagSet(CLASS_INITIALIZER_MAY_BE_POSTPONED_FLAG);
  }

  void markClassInitializerMayBePostponed() {
    setFlag(CLASS_INITIALIZER_MAY_BE_POSTPONED_FLAG);
  }

  @Override
  public TypeElement getDynamicUpperBoundType() {
    return returnsObjectWithUpperBoundType;
  }

  @Override
  public ClassTypeElement getDynamicLowerBoundType() {
    return returnsObjectWithLowerBoundType;
  }

  @Override
  public Set<DexType> getInitializedClassesOnNormalExit() {
    return initializedClassesOnNormalExit;
  }

  @Override
  public InstanceInitializerInfo getInstanceInitializerInfo() {
    return instanceInitializerInfo;
  }

  @Override
  public ParameterUsage getParameterUsages(int parameter) {
    return parametersUsages == null ? null : parametersUsages.getParameterUsage(parameter);
  }

  @Override
  public BitSet getNonNullParamOrThrow() {
    return nonNullParamOrThrow;
  }

  @Override
  public BitSet getNonNullParamOnNormalExits() {
    return nonNullParamOnNormalExits;
  }

  @Override
  public boolean hasBeenInlinedIntoSingleCallSite() {
    return isFlagSet(HAS_BEEN_INLINED_INTO_SINGLE_CALL_SITE_FLAG);
  }

  void markInlinedIntoSingleCallSite() {
    setFlag(HAS_BEEN_INLINED_INTO_SINGLE_CALL_SITE_FLAG);
  }

  @Override
  public boolean isReachabilitySensitive() {
    return isFlagSet(REACHABILITY_SENSITIVE_FLAG);
  }

  @Override
  public boolean returnsArgument() {
    return returnedArgument != -1;
  }

  @Override
  public int getReturnedArgument() {
    assert returnsArgument();
    return returnedArgument;
  }

  @Override
  public boolean neverReturnsNormally() {
    return isFlagSet(NEVER_RETURNS_NORMALLY_FLAG);
  }

  @Override
  public BridgeInfo getBridgeInfo() {
    return bridgeInfo;
  }

  void setBridgeInfo(BridgeInfo bridgeInfo) {
    this.bridgeInfo = bridgeInfo;
  }

  @Override
  public ClassInlinerEligibilityInfo getClassInlinerEligibility() {
    return classInlinerEligibility;
  }

  @Override
  public AbstractValue getAbstractReturnValue() {
    return abstractReturnValue;
  }

  @Override
  public boolean isInitializerEnablingJavaVmAssertions() {
    return isFlagSet(INITIALIZER_ENABLING_JAVA_ASSERTIONS_FLAG);
  }

  @Override
  public boolean forceInline() {
    return inlining == InlinePreference.ForceInline;
  }

  @Override
  public boolean neverInline() {
    return inlining == InlinePreference.NeverInline;
  }

  @Override
  public boolean checksNullReceiverBeforeAnySideEffect() {
    return isFlagSet(CHECKS_NULL_RECEIVER_BEFORE_ANY_SIDE_EFFECT_FLAG);
  }

  @Override
  public boolean triggersClassInitBeforeAnySideEffect() {
    return isFlagSet(TRIGGERS_CLASS_INIT_BEFORE_ANY_SIDE_EFFECT_FLAG);
  }

  @Override
  public boolean mayHaveSideEffects() {
    return isFlagSet(MAY_HAVE_SIDE_EFFECT_FLAG);
  }

  @Override
  public boolean returnValueOnlyDependsOnArguments() {
    return isFlagSet(RETURN_VALUE_ONLY_DEPENDS_ON_ARGUMENTS_FLAG);
  }

  void setParameterUsages(ParameterUsagesInfo parametersUsages) {
    this.parametersUsages = parametersUsages;
  }

  void setNonNullParamOrThrow(BitSet facts) {
    this.nonNullParamOrThrow = facts;
  }

  void setNonNullParamOnNormalExits(BitSet facts) {
    this.nonNullParamOnNormalExits = facts;
  }

  public void setReachabilitySensitive(boolean reachabilitySensitive) {
    setFlag(REACHABILITY_SENSITIVE_FLAG, reachabilitySensitive);
  }

  void setClassInlinerEligibility(ClassInlinerEligibilityInfo eligibility) {
    this.classInlinerEligibility = eligibility;
  }

  void setInstanceInitializerInfo(InstanceInitializerInfo instanceInitializerInfo) {
    this.instanceInitializerInfo = instanceInitializerInfo;
  }

  void setInitializerEnablingJavaAssertions() {
    setFlag(INITIALIZER_ENABLING_JAVA_ASSERTIONS_FLAG);
  }

  void markInitializesClassesOnNormalExit(Set<DexType> initializedClassesOnNormalExit) {
    this.initializedClassesOnNormalExit = initializedClassesOnNormalExit;
  }

  void markReturnsArgument(int argument) {
    assert argument >= 0;
    assert returnedArgument == -1 || returnedArgument == argument;
    returnedArgument = argument;
  }

  void markMayNotHaveSideEffects() {
    clearFlag(MAY_HAVE_SIDE_EFFECT_FLAG);
  }

  void markReturnValueOnlyDependsOnArguments() {
    setFlag(RETURN_VALUE_ONLY_DEPENDS_ON_ARGUMENTS_FLAG);
  }

  void markNeverReturnsNormally() {
    setFlag(NEVER_RETURNS_NORMALLY_FLAG);
  }

  void markReturnsAbstractValue(AbstractValue value) {
    assert !abstractReturnValue.isSingleValue() || abstractReturnValue.equals(value)
        : "return single value changed from " + abstractReturnValue + " to " + value;
    abstractReturnValue = value;
  }

  void unsetAbstractReturnValue() {
    abstractReturnValue = UnknownValue.getInstance();
  }

  void markReturnsObjectWithUpperBoundType(AppView<?> appView, TypeElement type) {
    assert type != null;
    // We may get more precise type information if the method is reprocessed (e.g., due to
    // optimization info collected from all call sites), and hence the
    // `returnsObjectWithUpperBoundType` is allowed to become more precise.
    // TODO(b/142559221): non-materializable assume instructions?
    // Nullability could be less precise, though. For example, suppose a value is known to be
    // non-null after a safe invocation, hence recorded with the non-null variant. If that call is
    // inlined and the method is reprocessed, such non-null assumption cannot be made again.
    assert returnsObjectWithUpperBoundType == DefaultMethodOptimizationInfo.UNKNOWN_TYPE
            || type.lessThanOrEqualUpToNullability(returnsObjectWithUpperBoundType, appView)
        : "upper bound type changed from " + returnsObjectWithUpperBoundType + " to " + type;
    returnsObjectWithUpperBoundType = type;
    if (type.isNullType()) {
      returnsObjectWithLowerBoundType = null;
    }
  }

  void markReturnsObjectWithLowerBoundType(ClassTypeElement type) {
    assert type != null;
    // Currently, we only have a lower bound type when we have _exact_ runtime type information.
    // Thus, the type should never become more precise (although the nullability could).
    assert returnsObjectWithLowerBoundType == DefaultMethodOptimizationInfo.UNKNOWN_CLASS_TYPE
            || type.equalUpToNullability(returnsObjectWithLowerBoundType)
        : "lower bound type changed from " + returnsObjectWithLowerBoundType + " to " + type;
    returnsObjectWithLowerBoundType = type;
  }

  // TODO(b/140214568): Should be package-private.
  public void markForceInline() {
    // For concurrent scenarios we should allow the flag to be already set
    assert inlining == InlinePreference.Default || inlining == InlinePreference.ForceInline;
    inlining = InlinePreference.ForceInline;
  }

  // TODO(b/140214568): Should be package-private.
  public void unsetForceInline() {
    // For concurrent scenarios we should allow the flag to be already unset
    assert inlining == InlinePreference.Default || inlining == InlinePreference.ForceInline;
    inlining = InlinePreference.Default;
  }

  // TODO(b/140214568): Should be package-private.
  public void markNeverInline() {
    // For concurrent scenarios we should allow the flag to be already set
    assert inlining == InlinePreference.Default || inlining == InlinePreference.NeverInline;
    inlining = InlinePreference.NeverInline;
  }

  void markCheckNullReceiverBeforeAnySideEffect(boolean mark) {
    setFlag(CHECKS_NULL_RECEIVER_BEFORE_ANY_SIDE_EFFECT_FLAG, mark);
  }

  void markTriggerClassInitBeforeAnySideEffect(boolean mark) {
    setFlag(TRIGGERS_CLASS_INIT_BEFORE_ANY_SIDE_EFFECT_FLAG, mark);
  }

  // TODO(b/140214568): Should be package-private.
  public void markAsPropagated() {
    setFlag(RETURN_VALUE_HAS_BEEN_PROPAGATED_FLAG);
  }

  @Override
  public boolean returnValueHasBeenPropagated() {
    return isFlagSet(RETURN_VALUE_HAS_BEEN_PROPAGATED_FLAG);
  }

  @Override
  public UpdatableMethodOptimizationInfo mutableCopy() {
    assert this != DefaultMethodOptimizationInfo.DEFAULT_INSTANCE;
    return new UpdatableMethodOptimizationInfo(this);
  }

  public void adjustOptimizationInfoAfterRemovingThisParameter() {
    // cannotBeKept: doesn't depend on `this`
    // classInitializerMayBePostponed: `this` could trigger <clinit> of the previous holder.
    clearFlag(CLASS_INITIALIZER_MAY_BE_POSTPONED_FLAG);
    // hasBeenInlinedIntoSingleCallSite: then it should not be staticized.
    clearFlag(HAS_BEEN_INLINED_INTO_SINGLE_CALL_SITE_FLAG);
    // initializedClassesOnNormalExit: `this` could trigger <clinit> of the previous holder.
    initializedClassesOnNormalExit =
        DefaultMethodOptimizationInfo.UNKNOWN_INITIALIZED_CLASSES_ON_NORMAL_EXIT;
    // At least, `this` pointer is not used in `returnedArgument`.
    assert returnedArgument == DefaultMethodOptimizationInfo.UNKNOWN_RETURNED_ARGUMENT
        || returnedArgument > 0;
    returnedArgument =
        returnedArgument == DefaultMethodOptimizationInfo.UNKNOWN_RETURNED_ARGUMENT
            ? DefaultMethodOptimizationInfo.UNKNOWN_RETURNED_ARGUMENT : returnedArgument - 1;
    // mayHaveSideEffects: `this` Argument didn't have side effects, so removing it doesn't affect
    //   whether or not the method may have side effects.
    // returnValueOnlyDependsOnArguments:
    //   if the method did before, so it does even after removing `this` Argument
    // code is not changed, and thus the following *return* info is not changed either.
    //   * neverReturnsNull
    //   * neverReturnsNormally
    //   * constantValue
    //   * returnsObjectWithUpperBoundType
    //   * returnsObjectWithLowerBoundType
    // inlining: it is not inlined, and thus staticized. No more chance of inlining, though.
    inlining = InlinePreference.Default;
    // checksNullReceiverBeforeAnySideEffect: no more receiver.
    markCheckNullReceiverBeforeAnySideEffect(
        DefaultMethodOptimizationInfo.UNKNOWN_CHECKS_NULL_RECEIVER_BEFORE_ANY_SIDE_EFFECT);
    // triggersClassInitBeforeAnySideEffect: code is not changed.
    markTriggerClassInitBeforeAnySideEffect(
        DefaultMethodOptimizationInfo.UNKNOWN_TRIGGERS_CLASS_INIT_BEFORE_ANY_SIDE_EFFECT);
    // classInlinerEligibility: chances are the method is not an instance method anymore.
    classInlinerEligibility = DefaultMethodOptimizationInfo.UNKNOWN_CLASS_INLINER_ELIGIBILITY;
    // initializerInfo: the computed initializer info may become invalid.
    instanceInitializerInfo = null;
    // initializerEnablingJavaAssertions: `this` could trigger <clinit> of the previous holder.
    setFlag(
        INITIALIZER_ENABLING_JAVA_ASSERTIONS_FLAG,
        DefaultMethodOptimizationInfo.UNKNOWN_INITIALIZER_ENABLING_JAVA_ASSERTIONS);
    parametersUsages =
        parametersUsages == DefaultMethodOptimizationInfo.UNKNOWN_PARAMETER_USAGE_INFO
            ? DefaultMethodOptimizationInfo.UNKNOWN_PARAMETER_USAGE_INFO
            : parametersUsages.remove(0);
    nonNullParamOrThrow =
        nonNullParamOrThrow == DefaultMethodOptimizationInfo.NO_NULL_PARAMETER_OR_THROW_FACTS
            ? DefaultMethodOptimizationInfo.NO_NULL_PARAMETER_OR_THROW_FACTS
            : nonNullParamOrThrow.get(1, nonNullParamOrThrow.length());
    nonNullParamOnNormalExits =
        nonNullParamOnNormalExits
                == DefaultMethodOptimizationInfo.NO_NULL_PARAMETER_ON_NORMAL_EXITS_FACTS
            ? DefaultMethodOptimizationInfo.NO_NULL_PARAMETER_ON_NORMAL_EXITS_FACTS
            : nonNullParamOnNormalExits.get(1, nonNullParamOnNormalExits.length());
    // reachabilitySensitive: doesn't depend on `this`
    // returnValueHasBeenPropagated: doesn't depend on `this`
  }
}
