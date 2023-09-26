// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import static java.util.Collections.emptySet;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.inlining.NeverSimpleInliningConstraint;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraint;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.classinliner.constraint.ClassInlinerMethodConstraint;
import com.android.tools.r8.ir.optimize.enums.classification.EnumUnboxerMethodClassification;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfoCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MaximumRemovedAndroidLogLevelRule;
import com.android.tools.r8.utils.BitSetUtils;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OptionalBool;
import java.util.BitSet;
import java.util.Collections;
import java.util.Set;

public class MutableMethodOptimizationInfo extends MethodOptimizationInfo
    implements MutableOptimizationInfo {

  private CallSiteOptimizationInfo argumentInfos = CallSiteOptimizationInfo.top();
  private Set<DexType> initializedClassesOnNormalExit =
      DefaultMethodOptimizationInfo.UNKNOWN_INITIALIZED_CLASSES_ON_NORMAL_EXIT;
  private int returnedArgument = DefaultMethodOptimizationInfo.UNKNOWN_RETURNED_ARGUMENT;
  private AbstractValue abstractReturnValue =
      DefaultMethodOptimizationInfo.UNKNOWN_ABSTRACT_RETURN_VALUE;
  private ClassInlinerMethodConstraint classInlinerConstraint =
      ClassInlinerMethodConstraint.alwaysFalse();
  private boolean convertCheckNotNull = false;
  private EnumUnboxerMethodClassification enumUnboxerMethodClassification =
      EnumUnboxerMethodClassification.unknown();
  private DynamicType dynamicType = DynamicType.unknown();
  private InlinePreference inlining = InlinePreference.Default;
  private OptionalBool isReturnValueUsed = OptionalBool.unknown();
  // Stores information about instance methods and constructors for
  // class inliner, null value indicates that the method is not eligible.
  private BridgeInfo bridgeInfo = null;
  private InstanceInitializerInfoCollection instanceInitializerInfoCollection =
      InstanceInitializerInfoCollection.empty();
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

  private SimpleInliningConstraint simpleInliningConstraint =
      NeverSimpleInliningConstraint.getInstance();

  private int maxRemovedAndroidLogLevel = MaximumRemovedAndroidLogLevelRule.NOT_SET;
  private BitSet parametersWithBitwiseOperations = null;
  private BitSet unusedArguments = null;

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
  private static final int NEVER_RETURNS_NORMALLY_FLAG = 0x20;
  private static final int INITIALIZER_ENABLING_JAVA_ASSERTIONS_FLAG = 0x80;
  private static final int RETURN_VALUE_HAS_BEEN_PROPAGATED_FLAG = 0x100;

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
    defaultFlags |=
        BooleanUtils.intValue(defaultOptInfo.neverReturnsNormally()) * NEVER_RETURNS_NORMALLY_FLAG;
    defaultFlags |=
        BooleanUtils.intValue(defaultOptInfo.isInitializerEnablingJavaVmAssertions())
            * INITIALIZER_ENABLING_JAVA_ASSERTIONS_FLAG;
    defaultFlags |=
        BooleanUtils.intValue(defaultOptInfo.returnValueHasBeenPropagated())
            * RETURN_VALUE_HAS_BEEN_PROPAGATED_FLAG;
    DEFAULT_FLAGS = defaultFlags;
  }

  private int flags = DEFAULT_FLAGS;

  MutableMethodOptimizationInfo() {
    // Intentionally left empty, just use the default values.
  }

  // Copy constructor used to create a mutable copy. Do not forget to copy from template when a new
  // field is added.
  private MutableMethodOptimizationInfo(MutableMethodOptimizationInfo template) {
    argumentInfos = template.argumentInfos;
    flags = template.flags;
    initializedClassesOnNormalExit = template.initializedClassesOnNormalExit;
    returnedArgument = template.returnedArgument;
    abstractReturnValue = template.abstractReturnValue;
    dynamicType = template.dynamicType;
    inlining = template.inlining;
    simpleInliningConstraint = template.simpleInliningConstraint;
    bridgeInfo = template.bridgeInfo;
    instanceInitializerInfoCollection = template.instanceInitializerInfoCollection;
    nonNullParamOrThrow = template.nonNullParamOrThrow;
    nonNullParamOnNormalExits = template.nonNullParamOnNormalExits;
    classInlinerConstraint = template.classInlinerConstraint;
    enumUnboxerMethodClassification = template.enumUnboxerMethodClassification;
    maxRemovedAndroidLogLevel = template.maxRemovedAndroidLogLevel;
  }

  public MutableMethodOptimizationInfo fixup(
      AppView<AppInfoWithLiveness> appView,
      DexEncodedMethod method,
      MethodOptimizationInfoFixer fixer) {
    return fixupArgumentInfos(method, fixer)
        .fixupBridgeInfo(fixer)
        .fixupClassInlinerMethodConstraint(appView, fixer)
        .fixupEnumUnboxerMethodClassification(fixer)
        .fixupInstanceInitializerInfo(appView, fixer)
        .fixupNonNullParamOnNormalExits(fixer)
        .fixupNonNullParamOrThrow(fixer)
        .fixupReturnedArgumentIndex(fixer)
        .fixupParametersWithBitwiseOperations(fixer)
        .fixupSimpleInliningConstraint(appView, fixer)
        .fixupUnusedArguments(fixer);
  }

  public MutableMethodOptimizationInfo fixupClassTypeReferences(
      AppView<AppInfoWithLiveness> appView, GraphLens lens) {
    return fixupClassTypeReferences(appView, lens, emptySet());
  }

  public MutableMethodOptimizationInfo fixupClassTypeReferences(
      AppView<AppInfoWithLiveness> appView, GraphLens lens, Set<DexType> prunedTypes) {
    dynamicType = dynamicType.rewrittenWithLens(appView, lens, prunedTypes);
    return this;
  }

  public MutableMethodOptimizationInfo fixupAbstractReturnValue(
      AppView<AppInfoWithLiveness> appView, GraphLens lens, GraphLens codeLens) {
    abstractReturnValue = abstractReturnValue.rewrittenWithLens(appView, lens, codeLens);
    return this;
  }

  public MutableMethodOptimizationInfo fixupInstanceInitializerInfo(
      AppView<AppInfoWithLiveness> appView,
      GraphLens lens,
      GraphLens codeLens,
      PrunedItems prunedItems) {
    instanceInitializerInfoCollection =
        instanceInitializerInfoCollection.rewrittenWithLens(appView, lens, codeLens, prunedItems);
    return this;
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

  void unsetClassInitializerMayBePostponed() {
    clearFlag(CLASS_INITIALIZER_MAY_BE_POSTPONED_FLAG);
  }

  @Override
  public CallSiteOptimizationInfo getArgumentInfos() {
    return argumentInfos;
  }

  public MutableMethodOptimizationInfo fixupArgumentInfos(
      DexEncodedMethod method, MethodOptimizationInfoFixer fixer) {
    if (argumentInfos.isConcreteCallSiteOptimizationInfo()) {
      return setArgumentInfos(
          method,
          fixer.fixupCallSiteOptimizationInfo(argumentInfos.asConcreteCallSiteOptimizationInfo()));
    }
    return this;
  }

  MutableMethodOptimizationInfo setArgumentInfos(
      // Method reference allows easily debugging when the optimization info for a given method
      // changes.
      @SuppressWarnings("unused") DexEncodedMethod method, CallSiteOptimizationInfo argumentInfos) {
    this.argumentInfos = argumentInfos;
    return this;
  }

  @Override
  public ClassInlinerMethodConstraint getClassInlinerMethodConstraint() {
    return classInlinerConstraint;
  }

  public MutableMethodOptimizationInfo fixupClassInlinerMethodConstraint(
      AppView<AppInfoWithLiveness> appView, MethodOptimizationInfoFixer fixer) {
    classInlinerConstraint =
        fixer.fixupClassInlinerMethodConstraint(appView, classInlinerConstraint);
    return this;
  }

  void setClassInlinerMethodConstraint(ClassInlinerMethodConstraint classInlinerConstraint) {
    this.classInlinerConstraint = classInlinerConstraint;
  }

  void unsetClassInlinerMethodConstraint() {
    this.classInlinerConstraint = ClassInlinerMethodConstraint.alwaysFalse();
  }

  void setConvertCheckNotNull() {
    this.convertCheckNotNull = true;
  }

  @Override
  public EnumUnboxerMethodClassification getEnumUnboxerMethodClassification() {
    return enumUnboxerMethodClassification;
  }

  public MutableMethodOptimizationInfo setEnumUnboxerMethodClassification(
      EnumUnboxerMethodClassification enumUnboxerMethodClassification) {
    // Check monotonicity.
    assert !this.enumUnboxerMethodClassification.isCheckNotNullClassification()
        || enumUnboxerMethodClassification.isCheckNotNullClassification();
    this.enumUnboxerMethodClassification = enumUnboxerMethodClassification;
    return this;
  }

  public void unsetEnumUnboxerMethodClassification() {
    this.enumUnboxerMethodClassification = EnumUnboxerMethodClassification.unknown();
  }

  public MutableMethodOptimizationInfo fixupEnumUnboxerMethodClassification(
      MethodOptimizationInfoFixer fixer) {
    enumUnboxerMethodClassification =
        fixer.fixupEnumUnboxerMethodClassification(enumUnboxerMethodClassification);
    return this;
  }

  @Override
  public DynamicType getDynamicType() {
    return dynamicType;
  }

  @Override
  public Set<DexType> getInitializedClassesOnNormalExit() {
    return initializedClassesOnNormalExit;
  }

  @Override
  public InstanceInitializerInfo getContextInsensitiveInstanceInitializerInfo() {
    return instanceInitializerInfoCollection.getContextInsensitive();
  }

  @Override
  public InstanceInitializerInfo getInstanceInitializerInfo(InvokeDirect invoke) {
    return instanceInitializerInfoCollection.get(invoke);
  }

  public MutableMethodOptimizationInfo fixupInstanceInitializerInfo(
      AppView<AppInfoWithLiveness> appView, MethodOptimizationInfoFixer fixer) {
    instanceInitializerInfoCollection =
        fixer.fixupInstanceInitializerInfo(appView, instanceInitializerInfoCollection);
    return this;
  }

  @Override
  public int getMaxRemovedAndroidLogLevel() {
    return maxRemovedAndroidLogLevel;
  }

  public void joinMaxRemovedAndroidLogLevel(int maxRemovedAndroidLogLevel) {
    this.maxRemovedAndroidLogLevel =
        MaximumRemovedAndroidLogLevelRule.joinMaxRemovedAndroidLogLevel(
            this.maxRemovedAndroidLogLevel, maxRemovedAndroidLogLevel);
  }

  @Override
  public BitSet getNonNullParamOrThrow() {
    return nonNullParamOrThrow;
  }

  public MutableMethodOptimizationInfo fixupNonNullParamOrThrow(MethodOptimizationInfoFixer fixer) {
    nonNullParamOrThrow = fixer.fixupNonNullParamOrThrow(nonNullParamOrThrow);
    return this;
  }

  void setNonNullParamOrThrow(BitSet facts) {
    this.nonNullParamOrThrow = facts;
  }

  void unsetNonNullParamOrThrow() {
    this.nonNullParamOrThrow = null;
  }

  @Override
  public BitSet getNonNullParamOnNormalExits() {
    return nonNullParamOnNormalExits;
  }

  public MutableMethodOptimizationInfo fixupNonNullParamOnNormalExits(
      MethodOptimizationInfoFixer fixer) {
    nonNullParamOnNormalExits = fixer.fixupNonNullParamOnNormalExits(nonNullParamOnNormalExits);
    return this;
  }

  void setNonNullParamOnNormalExits(BitSet facts) {
    this.nonNullParamOnNormalExits = facts;
  }

  void unsetNonNullParamOnNormalExits() {
    nonNullParamOnNormalExits = null;
  }

  @Override
  public boolean hasBeenInlinedIntoSingleCallSite() {
    return isFlagSet(HAS_BEEN_INLINED_INTO_SINGLE_CALL_SITE_FLAG);
  }

  void markInlinedIntoSingleCallSite() {
    setFlag(HAS_BEEN_INLINED_INTO_SINGLE_CALL_SITE_FLAG);
  }

  void unsetInlinedIntoSingleCallSite() {
    clearFlag(HAS_BEEN_INLINED_INTO_SINGLE_CALL_SITE_FLAG);
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

  public MutableMethodOptimizationInfo fixupBridgeInfo(MethodOptimizationInfoFixer fixer) {
    if (bridgeInfo != null) {
      bridgeInfo = fixer.fixupBridgeInfo(bridgeInfo);
    }
    return this;
  }

  void setBridgeInfo(BridgeInfo bridgeInfo) {
    this.bridgeInfo = bridgeInfo;
  }

  void unsetBridgeInfo() {
    this.bridgeInfo = null;
  }

  @Override
  public AbstractValue getAbstractReturnValue() {
    return abstractReturnValue;
  }

  @Override
  public SimpleInliningConstraint getNopInliningConstraint(InternalOptions options) {
    // We currently require that having a simple inlining constraint implies that the method becomes
    // empty after inlining. Therefore, an invoke is a nop if the simple inlining constraint is
    // satisfied (if the invoke does not trigger other side effects, such as class initialization).
    assert options.simpleInliningConstraintThreshold == 0;
    return getSimpleInliningConstraint();
  }

  @Override
  public SimpleInliningConstraint getSimpleInliningConstraint() {
    return simpleInliningConstraint;
  }

  @Override
  public boolean hasParametersWithBitwiseOperations() {
    return parametersWithBitwiseOperations != null;
  }

  @Override
  public BitSet getParametersWithBitwiseOperations() {
    return parametersWithBitwiseOperations;
  }

  public void setParametersWithBitwiseOperations(BitSet parametersWithBitwiseOperations) {
    if (parametersWithBitwiseOperations != null && !parametersWithBitwiseOperations.isEmpty()) {
      this.parametersWithBitwiseOperations = parametersWithBitwiseOperations;
    } else {
      this.parametersWithBitwiseOperations = null;
    }
  }

  public MutableMethodOptimizationInfo fixupParametersWithBitwiseOperations(
      MethodOptimizationInfoFixer fixer) {
    return fixupParametersWithBitwiseOperations(fixer.fixupArguments(unusedArguments));
  }

  public MutableMethodOptimizationInfo fixupParametersWithBitwiseOperations(
      BitSet parametersWithBitwiseOperations) {
    setParametersWithBitwiseOperations(parametersWithBitwiseOperations);
    return this;
  }

  @Override
  public BitSet getUnusedArguments() {
    return unusedArguments;
  }

  public MutableMethodOptimizationInfo fixupUnusedArguments(MethodOptimizationInfoFixer fixer) {
    return fixupUnusedArguments(fixer.fixupArguments(unusedArguments));
  }

  public MutableMethodOptimizationInfo fixupUnusedArguments(BitSet unusedArguments) {
    this.unusedArguments =
        unusedArguments != null && !unusedArguments.isEmpty() ? unusedArguments : null;
    return this;
  }

  void setUnusedArguments(BitSet unusedArguments) {
    // Verify monotonicity (i.e., unused arguments should never become used).
    assert !hasUnusedArguments() || unusedArguments != null;
    assert !hasUnusedArguments()
        || BitSetUtils.verifyLessThanOrEqualTo(getUnusedArguments(), unusedArguments);
    this.unusedArguments =
        unusedArguments != null && !unusedArguments.isEmpty() ? unusedArguments : null;
  }

  void unsetUnusedArguments() {
    unusedArguments = null;
  }

  @Override
  public boolean isConvertCheckNotNull() {
    return convertCheckNotNull;
  }

  @Override
  public boolean isInitializerEnablingJavaVmAssertions() {
    return isFlagSet(INITIALIZER_ENABLING_JAVA_ASSERTIONS_FLAG);
  }

  @Override
  public boolean isMultiCallerMethod() {
    return inlining == InlinePreference.MultiCallerInline;
  }

  @Override
  public OptionalBool isReturnValueUsed() {
    return isReturnValueUsed;
  }

  void setIsReturnValueUsed(OptionalBool isReturnValueUsed) {
    this.isReturnValueUsed = isReturnValueUsed;
  }

  @Override
  public boolean forceInline() {
    return inlining == InlinePreference.ForceInline;
  }

  @Override
  public boolean mayHaveSideEffects() {
    return isFlagSet(MAY_HAVE_SIDE_EFFECT_FLAG);
  }

  @Override
  public boolean mayHaveSideEffects(InvokeMethod invoke, InternalOptions options) {
    if (!mayHaveSideEffects()) {
      return false;
    }
    if (getNopInliningConstraint(options).isSatisfied(invoke)) {
      return false;
    }
    return true;
  }

  @Override
  public boolean returnValueOnlyDependsOnArguments() {
    return isFlagSet(RETURN_VALUE_ONLY_DEPENDS_ON_ARGUMENTS_FLAG);
  }

  void setSimpleInliningConstraint(SimpleInliningConstraint constraint) {
    this.simpleInliningConstraint = constraint;
  }

  void unsetSimpleInliningConstraint() {
    simpleInliningConstraint = NeverSimpleInliningConstraint.getInstance();
  }

  public MutableMethodOptimizationInfo fixupSimpleInliningConstraint(
      AppView<AppInfoWithLiveness> appView, MethodOptimizationInfoFixer fixer) {
    simpleInliningConstraint =
        fixer.fixupSimpleInliningConstraint(
            appView, simpleInliningConstraint, appView.simpleInliningConstraintFactory());
    return this;
  }

  void setInstanceInitializerInfoCollection(
      InstanceInitializerInfoCollection instanceInitializerInfoCollection) {
    this.instanceInitializerInfoCollection = instanceInitializerInfoCollection;
  }

  void unsetInstanceInitializerInfoCollection() {
    instanceInitializerInfoCollection = InstanceInitializerInfoCollection.empty();
  }

  void setInitializerEnablingJavaAssertions() {
    setFlag(INITIALIZER_ENABLING_JAVA_ASSERTIONS_FLAG);
  }

  void unsetInitializerEnablingJavaVmAssertions() {
    clearFlag(INITIALIZER_ENABLING_JAVA_ASSERTIONS_FLAG);
  }

  void markInitializesClassesOnNormalExit(Set<DexType> initializedClassesOnNormalExit) {
    this.initializedClassesOnNormalExit = initializedClassesOnNormalExit;
  }

  void unsetInitializedClassesOnNormalExit() {
    initializedClassesOnNormalExit = Collections.emptySet();
  }

  void markReturnsArgument(int returnedArgumentIndex) {
    assert returnedArgumentIndex >= 0;
    assert returnedArgument == -1 || returnedArgument == returnedArgumentIndex;
    returnedArgument = returnedArgumentIndex;
  }

  void unsetReturnedArgument() {
    returnedArgument = -1;
  }

  public MutableMethodOptimizationInfo fixupReturnedArgumentIndex(
      MethodOptimizationInfoFixer fixer) {
    returnedArgument = fixer.fixupReturnedArgumentIndex(returnedArgument);
    return this;
  }

  void markMayNotHaveSideEffects() {
    clearFlag(MAY_HAVE_SIDE_EFFECT_FLAG);
  }

  void unsetMayNotHaveSideEffects() {
    setFlag(MAY_HAVE_SIDE_EFFECT_FLAG);
  }

  void markReturnValueOnlyDependsOnArguments() {
    setFlag(RETURN_VALUE_ONLY_DEPENDS_ON_ARGUMENTS_FLAG);
  }

  void unsetReturnValueOnlyDependsOnArguments() {
    clearFlag(RETURN_VALUE_ONLY_DEPENDS_ON_ARGUMENTS_FLAG);
  }

  void markNeverReturnsNormally() {
    setFlag(NEVER_RETURNS_NORMALLY_FLAG);
  }

  void unsetNeverReturnsNormally() {
    clearFlag(NEVER_RETURNS_NORMALLY_FLAG);
  }

  public void markReturnsAbstractValue(AbstractValue value) {
    assert !abstractReturnValue.isSingleValue() || abstractReturnValue.equals(value)
        : "return single value changed from " + abstractReturnValue + " to " + value;
    abstractReturnValue = value;
  }

  void unsetAbstractReturnValue() {
    abstractReturnValue = UnknownValue.getInstance();
  }

  void setDynamicType(AppView<?> appView, DynamicType newDynamicType, DexEncodedMethod method) {
    setDynamicType(appView, newDynamicType, method.getReturnType().toTypeElement(appView));
  }

  public void setDynamicType(
      AppView<?> appView, DynamicType newDynamicType, TypeElement staticReturnType) {
    assert newDynamicType != null;
    // We may get more precise type information if the method is reprocessed (e.g., due to
    // optimization info collected from all call sites), and hence the
    // `returnsObjectWithUpperBoundType` is allowed to become more precise.
    // TODO(b/142559221): non-materializable assume instructions?
    // Nullability could be less precise, though. For example, suppose a value is known to be
    // non-null after a safe invocation, hence recorded with the non-null variant. If that call is
    // inlined and the method is reprocessed, such non-null assumption cannot be made again.
    assert verifyDynamicType(appView, newDynamicType, staticReturnType);
    dynamicType = newDynamicType;
  }

  private boolean verifyDynamicType(
      AppView<?> appView, DynamicType newDynamicType, TypeElement staticReturnType) {
    if (appView.enableWholeProgramOptimizations()) {
      TypeElement previousDynamicUpperBoundType =
          dynamicType.getDynamicUpperBoundType(staticReturnType);
      TypeElement newDynamicUpperBoundType =
          newDynamicType.getDynamicUpperBoundType(staticReturnType);
      assert newDynamicUpperBoundType.lessThanOrEqualUpToNullability(
              previousDynamicUpperBoundType, appView)
          : "upper bound type changed from "
              + previousDynamicUpperBoundType
              + " to "
              + newDynamicUpperBoundType;
    }
    return true;
  }

  void unsetDynamicType() {
    dynamicType = DynamicType.unknown();
  }

  // TODO(b/140214568): Should be package-private.
  public void markForceInline() {
    // For concurrent scenarios we should allow the flag to be already set
    assert inlining == InlinePreference.Default || inlining == InlinePreference.ForceInline;
    inlining = InlinePreference.ForceInline;
  }

  void unsetForceInline() {
    inlining = InlinePreference.Default;
  }

  void setMultiCallerMethod() {
    if (inlining == InlinePreference.Default) {
      inlining = InlinePreference.MultiCallerInline;
    } else {
      assert inlining == InlinePreference.ForceInline;
    }
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
  public boolean isMutableOptimizationInfo() {
    return true;
  }

  @Override
  public MutableMethodOptimizationInfo toMutableOptimizationInfo() {
    return this;
  }

  @Override
  public MutableMethodOptimizationInfo asMutableMethodOptimizationInfo() {
    return this;
  }

  public MutableMethodOptimizationInfo mutableCopy() {
    return new MutableMethodOptimizationInfo(this);
  }
}
