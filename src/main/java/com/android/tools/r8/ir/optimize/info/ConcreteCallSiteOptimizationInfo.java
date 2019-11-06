// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.Value;
import it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import java.util.List;

// Accumulated optimization info from call sites.
public class ConcreteCallSiteOptimizationInfo extends CallSiteOptimizationInfo {

  // inValues() size == DexMethod.arity + (isStatic ? 0 : 1) // receiver
  // That is, this information takes into account the receiver as well.
  private final int size;
  private final Int2ReferenceArrayMap<TypeLatticeElement> dynamicUpperBoundTypes;
  // TODO(b/69963623): sparse map from index to ConstantData if any.

  private ConcreteCallSiteOptimizationInfo(DexEncodedMethod encodedMethod) {
    assert encodedMethod.method.getArity() + (encodedMethod.isStatic() ? 0 : 1) > 0;
    this.size = encodedMethod.method.getArity() + (encodedMethod.isStatic() ? 0 : 1);
    this.dynamicUpperBoundTypes = new Int2ReferenceArrayMap<>(size);
  }

  private ConcreteCallSiteOptimizationInfo(int size) {
    this.size = size;
    this.dynamicUpperBoundTypes = new Int2ReferenceArrayMap<>(size);
  }

  CallSiteOptimizationInfo join(
      ConcreteCallSiteOptimizationInfo other, AppView<?> appView, DexEncodedMethod encodedMethod) {
    assert this.size == other.size;
    ConcreteCallSiteOptimizationInfo result = new ConcreteCallSiteOptimizationInfo(this.size);
    assert result.dynamicUpperBoundTypes != null;
    for (int i = 0; i < result.size; i++) {
      TypeLatticeElement thisUpperBoundType = getDynamicUpperBoundType(i);
      if (thisUpperBoundType == null) {
        // This means the corresponding argument is primitive. The counterpart should be too.
        assert other.getDynamicUpperBoundType(i) == null;
        continue;
      }
      assert thisUpperBoundType.isReference();
      TypeLatticeElement otherUpperBoundType = other.getDynamicUpperBoundType(i);
      assert otherUpperBoundType != null && otherUpperBoundType.isReference();
      result.dynamicUpperBoundTypes.put(
          i, thisUpperBoundType.join(otherUpperBoundType, appView));
    }
    if (result.hasUsefulOptimizationInfo(appView, encodedMethod)) {
      return result;
    }
    // As soon as we know the argument collection so far does not have any useful optimization info,
    // move to TOP so that further collection can be simply skipped.
    return TOP;
  }

  private TypeLatticeElement[] getStaticTypes(AppView<?> appView, DexEncodedMethod encodedMethod) {
    int argOffset = encodedMethod.isStatic() ? 0 : 1;
    int size = encodedMethod.method.getArity() + argOffset;
    TypeLatticeElement[] staticTypes = new TypeLatticeElement[size];
    if (!encodedMethod.isStatic()) {
      staticTypes[0] =
          TypeLatticeElement.fromDexType(
              encodedMethod.method.holder, definitelyNotNull(), appView);
    }
    for (int i = 0; i < encodedMethod.method.getArity(); i++) {
      staticTypes[i + argOffset] =
          TypeLatticeElement.fromDexType(
              encodedMethod.method.proto.parameters.values[i], maybeNull(), appView);
    }
    return staticTypes;
  }

  @Override
  public boolean hasUsefulOptimizationInfo(AppView<?> appView, DexEncodedMethod encodedMethod) {
    TypeLatticeElement[] staticTypes = getStaticTypes(appView, encodedMethod);
    for (int i = 0; i < size; i++) {
      if (!staticTypes[i].isReference()) {
        continue;
      }
      TypeLatticeElement dynamicUpperBoundType = getDynamicUpperBoundType(i);
      if (dynamicUpperBoundType == null) {
        continue;
      }
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
  public TypeLatticeElement getDynamicUpperBoundType(int argIndex) {
    assert 0 <= argIndex && argIndex < size;
    return dynamicUpperBoundTypes.getOrDefault(argIndex, null);
  }

  public static CallSiteOptimizationInfo fromArguments(
      AppView<? extends AppInfoWithSubtyping> appView,
      DexEncodedMethod method,
      List<Value> inValues) {
    ConcreteCallSiteOptimizationInfo newCallSiteInfo = new ConcreteCallSiteOptimizationInfo(method);
    assert newCallSiteInfo.size == inValues.size();
    for (int i = 0; i < newCallSiteInfo.size; i++) {
      Value arg = inValues.get(i);
      // TODO(b/69963623): may need different place to store constants.
      if (arg.getTypeLattice().isPrimitive()) {
        continue;
      }
      assert arg.getTypeLattice().isReference();
      newCallSiteInfo.dynamicUpperBoundTypes.put(i, arg.getDynamicUpperBoundType(appView));
    }
    if (newCallSiteInfo.hasUsefulOptimizationInfo(appView, method)) {
      return newCallSiteInfo;
    }
    // As soon as we know the current call site does not have any useful optimization info,
    // return TOP so that further collection can be simply skipped.
    return TOP;
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
    assert this.dynamicUpperBoundTypes != null;
    return this.dynamicUpperBoundTypes.equals(otherInfo.dynamicUpperBoundTypes);
  }

  @Override
  public int hashCode() {
    assert this.dynamicUpperBoundTypes != null;
    return System.identityHashCode(dynamicUpperBoundTypes);
  }

  @Override
  public String toString() {
    return dynamicUpperBoundTypes.toString();
  }
}
