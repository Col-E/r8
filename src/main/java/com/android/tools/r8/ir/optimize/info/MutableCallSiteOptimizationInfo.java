// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MutableCallSiteOptimizationInfo extends CallSiteOptimizationInfo {

  // inValues() size == DexMethod.arity + (isStatic ? 0 : 1) // receiver
  // That is, this information takes into account the receiver as well.
  private final int size;
  // Mappings from the calling context to argument collection. Note that, even in the same context,
  // the corresponding method can be invoked multiple times with different arguments, hence join of
  // argument collections.
  private final Map<DexEncodedMethod, ArgumentCollection> callSiteInfos = new ConcurrentHashMap<>();
  private ArgumentCollection cachedRepresentative = null;

  private static class ArgumentCollection {

    TypeLatticeElement[] dynamicTypes;

    private static final ArgumentCollection BOTTOM = new ArgumentCollection() {
      @Override
      TypeLatticeElement getDynamicType(int index) {
        return TypeLatticeElement.BOTTOM;
      }

      @Override
      public int hashCode() {
        return System.identityHashCode(this);
      }

      @Override
      public String toString() {
        return "(BOTTOM)";
      }
    };

    private ArgumentCollection() {}

    ArgumentCollection(int size) {
      this.dynamicTypes = new TypeLatticeElement[size];
      Arrays.fill(this.dynamicTypes, TypeLatticeElement.BOTTOM);
    }

    TypeLatticeElement getDynamicType(int index) {
      assert dynamicTypes != null;
      assert 0 <= index && index < dynamicTypes.length;
      return dynamicTypes[index];
    }

    ArgumentCollection copy() {
      ArgumentCollection copy = new ArgumentCollection();
      copy.dynamicTypes = new TypeLatticeElement[this.dynamicTypes.length];
      System.arraycopy(this.dynamicTypes, 0, copy.dynamicTypes, 0, this.dynamicTypes.length);
      return copy;
    }

    ArgumentCollection join(ArgumentCollection other, AppView<?> appView) {
      if (other == BOTTOM) {
        return this;
      }
      if (this == BOTTOM) {
        return other;
      }
      assert this.dynamicTypes.length == other.dynamicTypes.length;
      ArgumentCollection result = this.copy();
      for (int i = 0; i < result.dynamicTypes.length; i++) {
        result.dynamicTypes[i] = result.dynamicTypes[i].join(other.dynamicTypes[i], appView);
      }
      return result;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ArgumentCollection)) {
        return false;
      }
      ArgumentCollection otherCollection = (ArgumentCollection) other;
      if (this == BOTTOM || otherCollection == BOTTOM) {
        return this == BOTTOM && otherCollection == BOTTOM;
      }
      return Arrays.equals(this.dynamicTypes, otherCollection.dynamicTypes);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(dynamicTypes);
    }

    @Override
    public String toString() {
      return "(" + StringUtils.join(Arrays.asList(dynamicTypes), ", ") + ")";
    }
  }

  public MutableCallSiteOptimizationInfo(DexEncodedMethod encodedMethod) {
    assert encodedMethod.method.getArity() > 0;
    this.size = encodedMethod.method.getArity() + (encodedMethod.isStatic() ? 0 : 1);
  }

  private void computeCachedRepresentativeIfNecessary(AppView<?> appView) {
    if (cachedRepresentative == null && !callSiteInfos.isEmpty()) {
      synchronized (callSiteInfos) {
        // Make sure collected information is not flushed out by other threads.
        if (!callSiteInfos.isEmpty()) {
          cachedRepresentative =
              callSiteInfos.values().stream()
                  .reduce(
                      ArgumentCollection.BOTTOM,
                      (prev, next) -> prev.join(next, appView),
                      (prev, next) -> prev.join(next, appView));
          // After creating a cached representative, flush out the collected information.
          callSiteInfos.clear();
        } else {
          // If collected information is gone while waiting for the lock, make sure it's used to
          // compute the cached representative.
          assert cachedRepresentative != null;
        }
      }
    }
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
    computeCachedRepresentativeIfNecessary(appView);
    TypeLatticeElement[] staticTypes = getStaticTypes(appView, encodedMethod);
    for (int i = 0; i < size; i++) {
      if (!staticTypes[i].isReference()) {
        continue;
      }
      TypeLatticeElement dynamicType = getDynamicType(i);
      if (dynamicType == null) {
        continue;
      }
      // To avoid the full join of type lattices below, separately check if the nullability of
      // arguments is improved, and if so, we can eagerly conclude that we've collected useful
      // call site information for this method.
      Nullability nullability = dynamicType.nullability();
      if (nullability.isDefinitelyNull()) {
        return true;
      }
      // In general, though, we're looking for (strictly) better dynamic types for arguments.
      if (dynamicType.strictlyLessThan(staticTypes[i], appView)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public TypeLatticeElement getDynamicType(int argIndex) {
    assert 0 <= argIndex && argIndex < size;
    if (cachedRepresentative == null) {
      return null;
    }
    return cachedRepresentative.getDynamicType(argIndex);
  }

  public static boolean hasArgumentsToRecord(List<Value> inValues) {
    // TODO(b/69963623): allow primitive types with compile-time constants.
    for (Value v : inValues) {
      if (v.getTypeLattice().isReference()) {
        return true;
      }
    }
    return false;
  }

  public void recordArguments(
      AppView<?> appView, DexEncodedMethod callingContext, List<Value> inValues) {
    assert cachedRepresentative == null;
    assert size == inValues.size();
    ArgumentCollection newCallSiteInfo = new ArgumentCollection(size);
    for (int i = 0; i < size; i++) {
      newCallSiteInfo.dynamicTypes[i] = inValues.get(i).getTypeLattice();
    }
    assert callingContext != null;
    ArgumentCollection accumulatedArgumentCollection =
        callSiteInfos.computeIfAbsent(callingContext, ignore -> ArgumentCollection.BOTTOM);
    callSiteInfos.put(
        callingContext, accumulatedArgumentCollection.join(newCallSiteInfo, appView));
  }

  @Override
  public boolean isMutableCallSiteOptimizationInfo() {
    return true;
  }

  @Override
  public MutableCallSiteOptimizationInfo asMutableCallSiteOptimizationInfo() {
    return this;
  }

  @Override
  public String toString() {
    if (cachedRepresentative != null) {
      return cachedRepresentative.toString();
    }
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<DexEncodedMethod, ArgumentCollection> entry : callSiteInfos.entrySet()) {
      builder.append(entry.getKey().toSourceString());
      builder.append(" -> ");
      builder.append(entry.getValue().toString());
      builder.append(System.lineSeparator());
    }
    return builder.toString();
  }
}
