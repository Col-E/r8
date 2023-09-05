// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.AbstractFieldSet;
import com.android.tools.r8.ir.analysis.modeling.LibraryMethodReadSetModeling;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.optimize.DefaultInliningOracle;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

public abstract class InvokeMethod extends Invoke {

  private final DexMethod method;

  public InvokeMethod(DexMethod target, Value result, List<Value> arguments) {
    super(result, arguments);
    this.method = target;
  }

  public static InvokeMethod create(
      InvokeType type, DexMethod target, Value result, List<Value> arguments, boolean itf) {
    switch (type) {
      case DIRECT:
        return new InvokeDirect(target, result, arguments, itf);
      case INTERFACE:
        return new InvokeInterface(target, result, arguments);
      case STATIC:
        return new InvokeStatic(target, result, arguments, itf);
      case SUPER:
        return new InvokeSuper(target, result, arguments, itf);
      case VIRTUAL:
        assert !itf;
        return new InvokeVirtual(target, result, arguments);
      case CUSTOM:
      case MULTI_NEW_ARRAY:
      case NEW_ARRAY:
      case POLYMORPHIC:
      default:
        throw new Unreachable("Unexpected invoke type: " + type);
    }
  }

  public Value getFirstNonReceiverArgument() {
    return getArgument(getFirstNonReceiverArgumentIndex());
  }

  public int getFirstNonReceiverArgumentIndex() {
    return BooleanUtils.intValue(isInvokeMethodWithReceiver());
  }

  public abstract boolean getInterfaceBit();

  @Override
  public DexType getReturnType() {
    return method.proto.returnType;
  }

  public DexMethod getInvokedMethod() {
    return method;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isInvokeMethod() && method == other.asInvokeMethod().getInvokedMethod();
  }

  @Override
  public String toString() {
    return super.toString() + "; method: " + method.toSourceString();
  }

  @Override
  public boolean isInvokeMethod() {
    return true;
  }

  @Override
  public InvokeMethod asInvokeMethod() {
    return this;
  }

  // In subclasses, e.g., invoke-virtual or invoke-super, use a narrower receiver type by using
  // receiver type and calling context---the holder of the method where the current invocation is.
  // TODO(b/140204899): Refactor lookup methods to be defined in a single place.
  public abstract DexClassAndMethod lookupSingleTarget(AppView<?> appView, ProgramMethod context);

  public final ProgramMethod lookupSingleProgramTarget(AppView<?> appView, ProgramMethod context) {
    return DexClassAndMethod.asProgramMethodOrNull(lookupSingleTarget(appView, context));
  }

  // TODO(b/140204899): Refactor lookup methods to be defined in a single place.
  public ProgramMethodSet lookupProgramDispatchTargets(
      AppView<AppInfoWithLiveness> appView, ProgramMethod context) {
    if (!getInvokedMethod().holder.isClassType()) {
      return null;
    }
    if (!isInvokeMethodWithDynamicDispatch()) {
      ProgramMethod singleTarget = lookupSingleProgramTarget(appView, context);
      return singleTarget != null ? ProgramMethodSet.create(singleTarget) : null;
    }
    DexProgramClass refinedReceiverUpperBound =
        asProgramClassOrNull(
            appView.definitionFor(
                TypeAnalysis.getRefinedReceiverType(appView, asInvokeMethodWithReceiver())));
    DexProgramClass refinedReceiverLowerBound = null;
    ClassTypeElement refinedReceiverLowerBoundType =
        asInvokeMethodWithReceiver().getReceiver().getDynamicLowerBoundType(appView);
    if (refinedReceiverLowerBoundType != null) {
      refinedReceiverLowerBound =
          asProgramClassOrNull(appView.definitionFor(refinedReceiverLowerBoundType.getClassType()));
      // TODO(b/154822960): Check if the lower bound is a subtype of the upper bound.
      if (refinedReceiverUpperBound != null
          && refinedReceiverLowerBound != null
          && !appView
              .appInfo()
              .isSubtype(refinedReceiverLowerBound.type, refinedReceiverUpperBound.type)) {
        refinedReceiverLowerBound = null;
      }
    }
    MethodResolutionResult resolutionResult =
        appView.appInfo().resolveMethodLegacy(method, getInterfaceBit());
    LookupResult lookupResult;
    if (refinedReceiverUpperBound != null) {
      lookupResult =
          resolutionResult.lookupVirtualDispatchTargets(
              context.getHolder(), appView, refinedReceiverUpperBound, refinedReceiverLowerBound);
    } else {
      lookupResult = resolutionResult.lookupVirtualDispatchTargets(context.getHolder(), appView);
    }
    if (lookupResult.isLookupResultFailure()) {
      return null;
    }
    ProgramMethodSet result = ProgramMethodSet.create();
    lookupResult.forEach(
        target -> {
          DexClassAndMethod methodTarget = target.getTarget();
          if (methodTarget.isProgramMethod()) {
            result.add(methodTarget.asProgramMethod());
          }
        },
        lambda -> {
          // TODO(b/150277553): Support lambda targets.
        });
    return result;
  }

  public abstract InlineAction computeInlining(
      ProgramMethod singleTarget,
      Reason reason,
      DefaultInliningOracle decider,
      ClassInitializationAnalysis classInitializationAnalysis,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter);

  @Override
  public boolean identicalAfterRegisterAllocation(
      Instruction other, RegisterAllocator allocator, MethodConversionOptions conversionOptions) {
    if (!super.identicalAfterRegisterAllocation(other, allocator, conversionOptions)) {
      return false;
    }

    if (allocator.options().canHaveIncorrectJoinForArrayOfInterfacesBug()) {
      InvokeMethod invoke = other.asInvokeMethod();

      // If one of the arguments of this invoke is an array, then make sure that the corresponding
      // argument of the other invoke is the exact same value. Otherwise, the verifier may
      // incorrectly join the types of these arrays to Object[].
      for (int i = 0; i < arguments().size(); ++i) {
        Value argument = arguments().get(i);
        if (argument.getType().isArrayType() && argument != invoke.arguments().get(i)) {
          return false;
        }
      }
    }

    return true;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.loadInValues(this, it);
    if (getReturnType().isVoidType()) {
      return;
    }
    if (outValue == null) {
      helper.popOutType(getReturnType(), this, it);
    } else {
      assert outValue.isUsed();
      helper.storeOutValue(this, it);
    }
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  public DexType computeVerificationType(AppView<?> appView, TypeVerificationHelper helper) {
    return getReturnType();
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return true;
  }

  @Override
  public AbstractFieldSet readSet(AppView<AppInfoWithLiveness> appView, ProgramMethod context) {
    return LibraryMethodReadSetModeling.getModeledReadSetOrUnknown(appView, this);
  }

  @Override
  public AbstractValue getAbstractValue(
      AppView<?> appView, ProgramMethod context, AbstractValueSupplier abstractValueSupplier) {
    assert hasOutValue();
    DexClassAndMethod method = lookupSingleTarget(appView, context);
    if (method != null) {
      return method.getDefinition().getOptimizationInfo().getAbstractReturnValue();
    }
    return UnknownValue.getInstance();
  }

  @SuppressWarnings("ReferenceEquality")
  boolean verifyD8LookupResult(
      DexEncodedMethod hierarchyResult, DexEncodedMethod lookupDirectTargetOnItself) {
    if (lookupDirectTargetOnItself == null) {
      return true;
    }
    assert lookupDirectTargetOnItself == hierarchyResult;
    return true;
  }

  @Override
  public boolean throwsNpeIfValueIsNull(Value value, AppView<?> appView, ProgramMethod context) {
    DexClassAndMethod singleTarget = lookupSingleTarget(appView, context);
    if (singleTarget != null) {
      BitSet nonNullParamOrThrow =
          singleTarget.getDefinition().getOptimizationInfo().getNonNullParamOrThrow();
      if (nonNullParamOrThrow != null) {
        int argumentIndex = inValues.indexOf(value);
        return argumentIndex >= 0 && nonNullParamOrThrow.get(argumentIndex);
      }
    }
    return false;
  }

  abstract static class Builder<B extends Builder<B, I>, I extends InvokeMethod>
      extends BuilderBase<B, I> {

    protected DexMethod method;
    protected List<Value> arguments = Collections.emptyList();

    public B setArguments(Value... arguments) {
      return setArguments(Arrays.asList(arguments));
    }

    public B setArguments(List<Value> arguments) {
      assert arguments != null;
      this.arguments = arguments;
      return self();
    }

    public B setFreshOutValue(AppView<?> appView, ValueFactory factory) {
      return super.setFreshOutValue(
          factory, TypeElement.fromDexType(method.getReturnType(), maybeNull(), appView));
    }

    public B setSingleArgument(Value argument) {
      return setArguments(ImmutableList.of(argument));
    }

    public B setMethod(DexMethod method) {
      this.method = method;
      return self();
    }

    public B setMethod(DexClassAndMethod method) {
      return setMethod(method.getReference());
    }
  }
}
