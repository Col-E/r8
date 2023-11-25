// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info;

import static com.android.tools.r8.utils.MapUtils.canonicalizeEmptyMap;
import static java.util.Objects.requireNonNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.proto.RewrittenTypeInfo;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ParameterState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;

// Accumulated optimization info from call sites.
public class ConcreteCallSiteOptimizationInfo extends CallSiteOptimizationInfo {

  // inValues() size == DexMethod.arity + (isStatic ? 0 : 1) // receiver
  // That is, this information takes into account the receiver as well.
  private final int size;
  private final Int2ReferenceMap<DynamicType> dynamicTypes;
  private final Int2ReferenceMap<AbstractValue> constants;

  private ConcreteCallSiteOptimizationInfo(int size) {
    this(size, new Int2ReferenceArrayMap<>(size), new Int2ReferenceArrayMap<>(size));
  }

  private ConcreteCallSiteOptimizationInfo(
      int size,
      Int2ReferenceMap<DynamicType> dynamicTypes,
      Int2ReferenceMap<AbstractValue> constants) {
    assert size > 0;
    assert constants.values().stream().noneMatch(AbstractValue::isUnknown);
    assert dynamicTypes.values().stream().noneMatch(DynamicType::isUnknown);
    this.size = size;
    this.dynamicTypes = requireNonNull(dynamicTypes);
    this.constants = requireNonNull(constants);
  }

  private static CallSiteOptimizationInfo create(
      int size,
      Int2ReferenceMap<DynamicType> dynamicTypes,
      Int2ReferenceMap<AbstractValue> constants) {
    return constants.isEmpty() && dynamicTypes.isEmpty()
        ? top()
        : new ConcreteCallSiteOptimizationInfo(
            size, canonicalizeEmptyMap(dynamicTypes), canonicalizeEmptyMap(constants));
  }

  public CallSiteOptimizationInfo fixupAfterParametersChanged(
      RewrittenPrototypeDescription prototypeChanges) {
    if (prototypeChanges.isEmpty()) {
      return this;
    }

    ArgumentInfoCollection parameterChanges = prototypeChanges.getArgumentInfoCollection();
    if (parameterChanges.isEmpty()) {
      if (prototypeChanges.hasExtraParameters()) {
        return new ConcreteCallSiteOptimizationInfo(
            size + prototypeChanges.numberOfExtraParameters(), dynamicTypes, constants);
      }
      return this;
    }

    assert parameterChanges.getRemovedParameterIndices().stream()
        .allMatch(removedParameterIndex -> removedParameterIndex < size);

    int newSizeAfterParameterRemoval = size - parameterChanges.numberOfRemovedArguments();
    if (newSizeAfterParameterRemoval == 0) {
      return top();
    }

    Int2ReferenceMap<AbstractValue> rewrittenConstants =
        new Int2ReferenceArrayMap<>(newSizeAfterParameterRemoval);
    Int2ReferenceMap<DynamicType> rewrittenDynamicTypes =
        new Int2ReferenceArrayMap<>(newSizeAfterParameterRemoval);
    for (int parameterIndex = 0, rewrittenParameterIndex = 0;
        parameterIndex < size;
        parameterIndex++) {
      if (!parameterChanges.isArgumentRemoved(parameterIndex)) {
        RewrittenTypeInfo rewrittenTypeInfo =
            parameterChanges.getArgumentInfo(parameterIndex).asRewrittenTypeInfo();
        if (rewrittenTypeInfo != null
            && rewrittenTypeInfo.getOldType().isReferenceType()
            && rewrittenTypeInfo.getNewType().isIntType()) {
          rewrittenParameterIndex++;
          continue;
        }
        AbstractValue abstractValue =
            constants.getOrDefault(parameterIndex, AbstractValue.unknown());
        if (!abstractValue.isUnknown()) {
          rewrittenConstants.put(rewrittenParameterIndex, abstractValue);
        }
        DynamicType dynamicType = dynamicTypes.get(parameterIndex);
        if (dynamicType != null) {
          rewrittenDynamicTypes.put(rewrittenParameterIndex, dynamicType);
        }
        rewrittenParameterIndex++;
      }
    }
    return ConcreteCallSiteOptimizationInfo.create(
        newSizeAfterParameterRemoval + prototypeChanges.numberOfExtraParameters(),
        rewrittenDynamicTypes,
        rewrittenConstants);
  }

  @Override
  public DynamicType getDynamicType(int argIndex) {
    assert 0 <= argIndex && argIndex < size;
    return dynamicTypes.getOrDefault(argIndex, DynamicType.unknown());
  }

  @Override
  public AbstractValue getAbstractArgumentValue(int argIndex) {
    assert 0 <= argIndex && argIndex < size;
    return constants.getOrDefault(argIndex, UnknownValue.getInstance());
  }

  public Nullability getNullability(int argIndex) {
    return getDynamicType(argIndex).getNullability();
  }

  public static CallSiteOptimizationInfo fromMethodState(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethod method,
      ConcreteMonomorphicMethodState methodState) {
    ConcreteCallSiteOptimizationInfo newCallSiteInfo =
        new ConcreteCallSiteOptimizationInfo(methodState.size());
    boolean isTop = true;
    for (int argumentIndex = 0; argumentIndex < methodState.size(); argumentIndex++) {
      ParameterState parameterState = methodState.getParameterState(argumentIndex);
      if (parameterState.isUnknown()) {
        continue;
      }

      ConcreteParameterState concreteParameterState = parameterState.asConcrete();

      // Constant propagation.
        AbstractValue abstractValue = concreteParameterState.getAbstractValue(appView);
        if (abstractValue.isNonTrivial()) {
          newCallSiteInfo.constants.put(argumentIndex, abstractValue);
          isTop = false;
        }

      // Type propagation.
      DexType staticType = method.getDefinition().getArgumentType(argumentIndex);
      if (staticType.isReferenceType()) {
        DynamicTypeWithUpperBound staticTypeElement = staticType.toDynamicType(appView);
        if (staticType.isArrayType()) {
          Nullability nullability = concreteParameterState.asArrayParameter().getNullability();
          if (nullability.isDefinitelyNull()) {
            newCallSiteInfo.constants.put(
                argumentIndex, appView.abstractValueFactory().createNullValue(staticType));
            isTop = false;
          } else if (nullability.isDefinitelyNotNull()) {
            newCallSiteInfo.dynamicTypes.put(
                argumentIndex, staticTypeElement.withNullability(Nullability.definitelyNotNull()));
            isTop = false;
          } else {
            // The nullability should never be unknown, since we should use the unknown method state
            // in this case. It should also not be bottom, since we should change the method's body
            // to throw null in this case.
            assert false;
          }
        } else if (staticType.isClassType()) {
          DynamicType dynamicType = concreteParameterState.asReferenceParameter().getDynamicType();
          if (!dynamicType.isUnknown()) {
            newCallSiteInfo.dynamicTypes.put(argumentIndex, dynamicType);
            isTop = false;
          }
        }
      }
    }
    return isTop ? CallSiteOptimizationInfo.top() : newCallSiteInfo;
  }

  @Override
  public boolean isConcreteCallSiteOptimizationInfo() {
    return true;
  }

  @Override
  public ConcreteCallSiteOptimizationInfo asConcreteCallSiteOptimizationInfo() {
    return this;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof ConcreteCallSiteOptimizationInfo)) {
      return false;
    }
    ConcreteCallSiteOptimizationInfo otherInfo = (ConcreteCallSiteOptimizationInfo) other;
    return dynamicTypes.equals(otherInfo.dynamicTypes) && constants.equals(otherInfo.constants);
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(dynamicTypes) * 7 + System.identityHashCode(constants);
  }

  @Override
  public String toString() {
    return dynamicTypes.toString()
        + (constants == null ? "" : (System.lineSeparator() + constants));
  }
}
