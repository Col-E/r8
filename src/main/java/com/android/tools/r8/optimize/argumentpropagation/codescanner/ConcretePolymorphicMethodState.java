// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ConcretePolymorphicMethodState extends ConcreteMethodState
    implements ConcretePolymorphicMethodStateOrBottom {

  private final Map<DynamicType, ConcreteMonomorphicMethodStateOrUnknown> receiverBoundsToState =
      new HashMap<>();

  public ConcretePolymorphicMethodState() {}

  public ConcretePolymorphicMethodState(
      DynamicType receiverBounds, ConcreteMonomorphicMethodStateOrUnknown methodState) {
    // TODO(b/190154391): Ensure that we use the unknown state instead of mapping unknown -> unknown
    //  here.
    receiverBoundsToState.put(receiverBounds, methodState);
  }

  private void add(
      AppView<AppInfoWithLiveness> appView,
      DynamicType bounds,
      ConcreteMonomorphicMethodStateOrUnknown methodState) {
    if (methodState.isUnknown()) {
      receiverBoundsToState.put(bounds, methodState);
    } else {
      assert methodState.isMonomorphic();
      receiverBoundsToState.compute(
          bounds,
          (ignore, existingState) -> {
            if (existingState == null) {
              return methodState.mutableCopy();
            }
            if (existingState.isUnknown()) {
              return existingState;
            }
            assert existingState.isMonomorphic();
            return existingState.asMonomorphic().mutableJoin(appView, methodState.asMonomorphic());
          });
    }
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

  public boolean isEmpty() {
    return receiverBoundsToState.isEmpty();
  }

  @Override
  public MethodState mutableCopy() {
    ConcretePolymorphicMethodState mutableCopy = new ConcretePolymorphicMethodState();
    forEach(
        (bounds, methodState) ->
            mutableCopy.receiverBoundsToState.put(bounds, methodState.mutableCopy()));
    return mutableCopy;
  }

  public MethodState mutableCopyWithRewrittenBounds(
      AppView<AppInfoWithLiveness> appView, Function<DynamicType, DynamicType> boundsRewriter) {
    ConcretePolymorphicMethodState mutableCopy = new ConcretePolymorphicMethodState();
    forEach(
        (bounds, methodState) -> {
          DynamicType rewrittenBounds = boundsRewriter.apply(bounds);
          if (rewrittenBounds != null) {
            mutableCopy.add(appView, rewrittenBounds, methodState);
          }
        });
    return mutableCopy.isEmpty() ? bottom() : mutableCopy;
  }

  public MethodState mutableJoin(
      AppView<AppInfoWithLiveness> appView, ConcretePolymorphicMethodState methodState) {
    assert !isEmpty();
    assert !methodState.isEmpty();
    methodState.receiverBoundsToState.forEach(
        (receiverBounds, stateToAdd) -> add(appView, receiverBounds, stateToAdd));
    // TODO(b/190154391): Widen to unknown when the unknown dynamic type is mapped to unknown.
    return this;
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
