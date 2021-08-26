// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ParameterState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.util.List;
import java.util.Objects;

// Accumulated optimization info from call sites.
public class ConcreteCallSiteOptimizationInfo extends CallSiteOptimizationInfo {

  // inValues() size == DexMethod.arity + (isStatic ? 0 : 1) // receiver
  // That is, this information takes into account the receiver as well.
  private final int size;
  private final Int2ReferenceMap<TypeElement> dynamicUpperBoundTypes;
  private final Int2ReferenceMap<AbstractValue> constants;

  private ConcreteCallSiteOptimizationInfo(int size, boolean allowConstantPropagation) {
    assert size > 0;
    this.size = size;
    this.dynamicUpperBoundTypes = new Int2ReferenceArrayMap<>(size);
    this.constants = allowConstantPropagation ? new Int2ReferenceArrayMap<>(size) : null;
  }

  CallSiteOptimizationInfo join(
      ConcreteCallSiteOptimizationInfo other, AppView<?> appView, DexEncodedMethod method) {
    assert size == other.size;
    assert size == method.getNumberOfArguments();
    boolean allowConstantPropagation =
        appView.options().callSiteOptimizationOptions().isConstantPropagationEnabled();
    ConcreteCallSiteOptimizationInfo result =
        new ConcreteCallSiteOptimizationInfo(size, allowConstantPropagation);
    for (int i = 0; i < result.size; i++) {
      if (allowConstantPropagation) {
        assert result.constants != null;
        AbstractValue abstractValue =
            getAbstractArgumentValue(i)
                .join(
                    other.getAbstractArgumentValue(i),
                    appView.abstractValueFactory(),
                    method.getArgumentType(i));
        if (abstractValue.isNonTrivial()) {
          result.constants.put(i, abstractValue);
        }
      }

      TypeElement thisUpperBoundType = getDynamicUpperBoundType(i);
      if (thisUpperBoundType == null) {
        // This means the corresponding argument is primitive. The counterpart should be too.
        assert other.getDynamicUpperBoundType(i) == null;
        continue;
      }
      assert thisUpperBoundType.isReferenceType();
      TypeElement otherUpperBoundType = other.getDynamicUpperBoundType(i);
      assert otherUpperBoundType != null && otherUpperBoundType.isReferenceType();
      result.dynamicUpperBoundTypes.put(
          i, thisUpperBoundType.join(otherUpperBoundType, appView));
    }
    if (result.hasUsefulOptimizationInfo(appView, method)) {
      return result;
    }
    // As soon as we know the argument collection so far does not have any useful optimization info,
    // move to TOP so that further collection can be simply skipped.
    return top();
  }

  private TypeElement[] getStaticTypes(AppView<?> appView, DexEncodedMethod method) {
    int argOffset = method.isStatic() ? 0 : 1;
    int size = method.getReference().getArity() + argOffset;
    TypeElement[] staticTypes = new TypeElement[size];
    if (!method.isStatic()) {
      staticTypes[0] =
          TypeElement.fromDexType(method.getHolderType(), definitelyNotNull(), appView);
    }
    for (int i = 0; i < method.getReference().getArity(); i++) {
      staticTypes[i + argOffset] =
          TypeElement.fromDexType(method.getParameter(i), maybeNull(), appView);
    }
    return staticTypes;
  }

  @Override
  public boolean hasUsefulOptimizationInfo(AppView<?> appView, DexEncodedMethod method) {
    TypeElement[] staticTypes = getStaticTypes(appView, method);
    for (int i = 0; i < size; i++) {
      AbstractValue abstractValue = getAbstractArgumentValue(i);
      if (abstractValue.isNonTrivial()) {
        assert appView.options().callSiteOptimizationOptions().isConstantPropagationEnabled();
        return true;
      }

      if (!staticTypes[i].isReferenceType()) {
        continue;
      }
      TypeElement dynamicUpperBoundType = getDynamicUpperBoundType(i);
      if (dynamicUpperBoundType == null) {
        continue;
      }
      assert appView.options().callSiteOptimizationOptions().isTypePropagationEnabled();
      // To avoid the full join of type lattices below, separately check if the nullability of
      // arguments is improved, and if so, we can eagerly conclude that we've collected useful
      // call site information for this method.
      Nullability nullability = dynamicUpperBoundType.nullability();
      if (nullability.isDefinitelyNull()) {
        return true;
      }
      // TODO(b/139246447): Similar to nullability, if dynamic lower bound type is available,
      //   we stop here and regard that call sites of this method have useful info.
      // In general, though, we're looking for (strictly) better dynamic types for arguments.
      if (dynamicUpperBoundType.strictlyLessThan(staticTypes[i], appView)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public TypeElement getDynamicUpperBoundType(int argIndex) {
    assert 0 <= argIndex && argIndex < size;
    assert dynamicUpperBoundTypes != null;
    return dynamicUpperBoundTypes.getOrDefault(argIndex, null);
  }

  @Override
  public AbstractValue getAbstractArgumentValue(int argIndex) {
    assert 0 <= argIndex && argIndex < size;
    // TODO(b/69963623): Remove this once enabled.
    if (constants == null) {
      return UnknownValue.getInstance();
    }
    return constants.getOrDefault(argIndex, UnknownValue.getInstance());
  }

  public static CallSiteOptimizationInfo fromArguments(
      AppView<AppInfoWithLiveness> appView,
      DexMethod invokedMethod,
      List<Value> arguments,
      ProgramMethod context) {
    boolean allowConstantPropagation =
        appView.options().callSiteOptimizationOptions().isConstantPropagationEnabled();
    ConcreteCallSiteOptimizationInfo newCallSiteInfo =
        new ConcreteCallSiteOptimizationInfo(arguments.size(), allowConstantPropagation);
    boolean hasReceiver = arguments.size() > invokedMethod.getArity();
    boolean isTop = true;
    assert newCallSiteInfo.dynamicUpperBoundTypes != null;
    for (int i = 0; i < newCallSiteInfo.size; i++) {
      Value arg = arguments.get(i);

      // Constant propagation.
      if (allowConstantPropagation) {
        assert newCallSiteInfo.constants != null;
        Value aliasedValue = arg.getAliasedValue();
        if (!aliasedValue.isPhi()) {
          AbstractValue abstractValue = aliasedValue.definition.getAbstractValue(appView, context);
          if (abstractValue.isNonTrivial()) {
            newCallSiteInfo.constants.put(i, abstractValue);
            isTop = false;
          }
        }
      }

      // Type propagation.
      if (arg.getType().isReferenceType()) {
        TypeElement staticType =
            TypeElement.fromDexType(
                hasReceiver ? invokedMethod.holder : invokedMethod.proto.getParameter(i),
                maybeNull(),
                appView);
        TypeElement dynamicUpperBoundType = arg.getDynamicUpperBoundType(appView);
        if (dynamicUpperBoundType != staticType) {
          newCallSiteInfo.dynamicUpperBoundTypes.put(i, dynamicUpperBoundType);
          isTop = false;
        } else {
          newCallSiteInfo.dynamicUpperBoundTypes.put(i, staticType);
        }
      }
    }
    return isTop ? CallSiteOptimizationInfo.top() : newCallSiteInfo;
  }

  public static CallSiteOptimizationInfo fromMethodState(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethod method,
      ConcreteMonomorphicMethodState methodState) {
    boolean allowConstantPropagation =
        appView.options().callSiteOptimizationOptions().isConstantPropagationEnabled();
    ConcreteCallSiteOptimizationInfo newCallSiteInfo =
        new ConcreteCallSiteOptimizationInfo(methodState.size(), allowConstantPropagation);
    boolean isTop = true;
    for (int argumentIndex = 0; argumentIndex < methodState.size(); argumentIndex++) {
      ParameterState parameterState = methodState.getParameterState(argumentIndex);
      if (parameterState.isUnknown()) {
        continue;
      }

      ConcreteParameterState concreteParameterState = parameterState.asConcrete();

      // Constant propagation.
      if (allowConstantPropagation) {
        AbstractValue abstractValue = concreteParameterState.getAbstractValue(appView);
        if (abstractValue.isNonTrivial()) {
          newCallSiteInfo.constants.put(argumentIndex, abstractValue);
          isTop = false;
        }
      }

      // Type propagation.
      DexType staticType = method.getDefinition().getArgumentType(argumentIndex);
      if (staticType.isReferenceType()) {
        TypeElement staticTypeElement = staticType.toTypeElement(appView);
        if (staticType.isArrayType()) {
          Nullability nullability = concreteParameterState.asArrayParameter().getNullability();
          if (nullability.isDefinitelyNull()) {
            if (allowConstantPropagation) {
              newCallSiteInfo.constants.put(
                  argumentIndex, appView.abstractValueFactory().createNullValue());
            } else {
              newCallSiteInfo.dynamicUpperBoundTypes.put(
                  argumentIndex, staticTypeElement.asArrayType().asDefinitelyNull());
            }
            isTop = false;
          } else if (nullability.isDefinitelyNotNull()) {
            newCallSiteInfo.dynamicUpperBoundTypes.put(
                argumentIndex, staticTypeElement.asArrayType().asDefinitelyNotNull());
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
            newCallSiteInfo.dynamicUpperBoundTypes.put(
                argumentIndex, dynamicType.getDynamicUpperBoundType());
            isTop = false;
          } else {
            newCallSiteInfo.dynamicUpperBoundTypes.put(argumentIndex, staticTypeElement);
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
    return Objects.equals(this.dynamicUpperBoundTypes, otherInfo.dynamicUpperBoundTypes)
        && Objects.equals(this.constants, otherInfo.constants);
  }

  @Override
  public int hashCode() {
    assert this.dynamicUpperBoundTypes != null;
    return System.identityHashCode(dynamicUpperBoundTypes) * 7 + System.identityHashCode(constants);
  }

  @Override
  public String toString() {
    return dynamicUpperBoundTypes.toString()
        + (constants == null ? "" : (System.lineSeparator() + constants.toString()));
  }
}
