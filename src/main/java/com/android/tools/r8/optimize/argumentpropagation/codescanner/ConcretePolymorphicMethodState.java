// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ConcretePolymorphicMethodState extends ConcreteMethodState
    implements ConcretePolymorphicMethodStateOrBottom, ConcretePolymorphicMethodStateOrUnknown {

  private final Map<DynamicType, ConcreteMonomorphicMethodStateOrUnknown> receiverBoundsToState;

  private ConcretePolymorphicMethodState(
      Map<DynamicType, ConcreteMonomorphicMethodStateOrUnknown> receiverBoundsToState) {
    this.receiverBoundsToState = receiverBoundsToState;
    assert !isEffectivelyBottom();
    assert !isEffectivelyUnknown();
  }

  private ConcretePolymorphicMethodState(
      DynamicType receiverBounds, ConcreteMonomorphicMethodStateOrUnknown methodState) {
    this.receiverBoundsToState = new HashMap<>(1);
    receiverBoundsToState.put(receiverBounds, methodState);
    assert !isEffectivelyUnknown();
  }

  public static ConcretePolymorphicMethodStateOrUnknown create(
      DynamicType receiverBounds, ConcreteMonomorphicMethodStateOrUnknown methodState) {
    return receiverBounds.isUnknown() && methodState.isUnknown()
        ? MethodState.unknown()
        : new ConcretePolymorphicMethodState(receiverBounds, methodState);
  }

  private ConcretePolymorphicMethodStateOrUnknown add(
      AppView<AppInfoWithLiveness> appView,
      DexMethodSignature methodSignature,
      DynamicType bounds,
      ConcreteMonomorphicMethodStateOrUnknown methodState,
      StateCloner cloner) {
    assert !isEffectivelyBottom();
    assert !isEffectivelyUnknown();
    if (methodState.isUnknown()) {
      if (bounds.isUnknown()) {
        return unknown();
      } else {
        receiverBoundsToState.put(bounds, methodState);
        return this;
      }
    } else {
      assert methodState.isMonomorphic();
      ConcreteMonomorphicMethodStateOrUnknown newMethodStateForBounds =
          joinInner(
              appView, methodSignature, receiverBoundsToState.get(bounds), methodState, cloner);
      if (bounds.isUnknown() && newMethodStateForBounds.isUnknown()) {
        return unknown();
      } else {
        receiverBoundsToState.put(bounds, newMethodStateForBounds);
        return this;
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static ConcreteMonomorphicMethodStateOrUnknown joinInner(
      AppView<AppInfoWithLiveness> appView,
      DexMethodSignature methodSignature,
      ConcreteMonomorphicMethodStateOrUnknown methodState,
      ConcreteMonomorphicMethodStateOrUnknown other,
      StateCloner cloner) {
    if (methodState == null) {
      return (ConcreteMonomorphicMethodStateOrUnknown) cloner.mutableCopy(other);
    }
    if (methodState.isUnknown() || other.isUnknown()) {
      return unknown();
    }
    assert methodState.isMonomorphic();
    return methodState
        .asMonomorphic()
        .mutableJoin(appView, methodSignature, other.asMonomorphic(), cloner);
  }

  public void forEach(
      BiConsumer<? super DynamicType, ? super ConcreteMonomorphicMethodStateOrUnknown> consumer) {
    receiverBoundsToState.forEach(consumer);
  }

  public MethodState getMethodStateForBounds(DynamicType dynamicType) {
    ConcreteMonomorphicMethodStateOrUnknown methodStateForBounds =
        receiverBoundsToState.get(dynamicType);
    if (methodStateForBounds != null) {
      return methodStateForBounds;
    }
    return MethodState.bottom();
  }

  public boolean isEffectivelyBottom() {
    return receiverBoundsToState.isEmpty();
  }

  public boolean isEffectivelyUnknown() {
    return getMethodStateForBounds(DynamicType.unknown()).isUnknown();
  }

  @Override
  public MethodState mutableCopy() {
    assert !isEffectivelyBottom();
    assert !isEffectivelyUnknown();
    Map<DynamicType, ConcreteMonomorphicMethodStateOrUnknown> receiverBoundsToState =
        new HashMap<>();
    forEach((bounds, methodState) -> receiverBoundsToState.put(bounds, methodState.mutableCopy()));
    return new ConcretePolymorphicMethodState(receiverBoundsToState);
  }

  public MethodState mutableCopyWithRewrittenBounds(
      AppView<AppInfoWithLiveness> appView,
      Function<DynamicType, DynamicType> boundsRewriter,
      DexMethodSignature methodSignature,
      StateCloner cloner) {
    assert !isEffectivelyBottom();
    assert !isEffectivelyUnknown();
    Map<DynamicType, ConcreteMonomorphicMethodStateOrUnknown> rewrittenReceiverBoundsToState =
        new HashMap<>();
    for (Entry<DynamicType, ConcreteMonomorphicMethodStateOrUnknown> entry :
        receiverBoundsToState.entrySet()) {
      DynamicType rewrittenBounds = boundsRewriter.apply(entry.getKey());
      if (rewrittenBounds == null) {
        continue;
      }
      ConcreteMonomorphicMethodStateOrUnknown existingMethodStateForBounds =
          rewrittenReceiverBoundsToState.get(rewrittenBounds);
      ConcreteMonomorphicMethodStateOrUnknown newMethodStateForBounds =
          joinInner(
              appView, methodSignature, existingMethodStateForBounds, entry.getValue(), cloner);
      if (rewrittenBounds.isUnknown() && newMethodStateForBounds.isUnknown()) {
        return unknown();
      }
      rewrittenReceiverBoundsToState.put(rewrittenBounds, newMethodStateForBounds);
    }
    return rewrittenReceiverBoundsToState.isEmpty()
        ? bottom()
        : new ConcretePolymorphicMethodState(rewrittenReceiverBoundsToState);
  }

  public MethodState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      DexMethodSignature methodSignature,
      ConcretePolymorphicMethodState methodState,
      StateCloner cloner) {
    assert !isEffectivelyBottom();
    assert !isEffectivelyUnknown();
    assert !methodState.isEffectivelyBottom();
    assert !methodState.isEffectivelyUnknown();
    for (Entry<DynamicType, ConcreteMonomorphicMethodStateOrUnknown> entry :
        methodState.receiverBoundsToState.entrySet()) {
      ConcretePolymorphicMethodStateOrUnknown result =
          add(appView, methodSignature, entry.getKey(), entry.getValue(), cloner);
      if (result.isUnknown()) {
        return result;
      }
      assert result == this;
    }
    assert !isEffectivelyUnknown();
    return this;
  }

  public Collection<ConcreteMonomorphicMethodStateOrUnknown> values() {
    return receiverBoundsToState.values();
  }

  @Override
  public boolean isPolymorphic() {
    return true;
  }

  @Override
  public ConcretePolymorphicMethodState asPolymorphic() {
    return this;
  }

  @Override
  public ConcretePolymorphicMethodStateOrBottom asPolymorphicOrBottom() {
    return this;
  }
}
