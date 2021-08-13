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

public class ConcretePolymorphicMethodState extends ConcreteMethodState {

  private final Map<DynamicType, ConcreteMonomorphicMethodStateOrUnknown> receiverBoundsToState =
      new HashMap<>();

  public ConcretePolymorphicMethodState(
      DynamicType receiverBounds, ConcreteMonomorphicMethodStateOrUnknown methodState) {
    // TODO(b/190154391): Ensure that we use the unknown state instead of mapping unknown -> unknown
    //  here.
    receiverBoundsToState.put(receiverBounds, methodState);
  }

  public void forEach(BiConsumer<DynamicType, MethodState> consumer) {
    receiverBoundsToState.forEach(consumer);
  }

  public boolean isEmpty() {
    return receiverBoundsToState.isEmpty();
  }

  public MethodState mutableJoin(
      AppView<AppInfoWithLiveness> appView, ConcretePolymorphicMethodState methodState) {
    assert !isEmpty();
    assert !methodState.isEmpty();
    methodState.receiverBoundsToState.forEach(
        (receiverBounds, stateToAdd) -> {
          if (stateToAdd.isUnknown()) {
            receiverBoundsToState.put(receiverBounds, stateToAdd);
          } else {
            assert stateToAdd.isMonomorphic();
            receiverBoundsToState.compute(
                receiverBounds,
                (ignore, existingState) -> {
                  if (existingState == null) {
                    return stateToAdd;
                  }
                  if (existingState.isUnknown()) {
                    return existingState;
                  }
                  assert existingState.isMonomorphic();
                  return existingState
                      .asMonomorphic()
                      .mutableJoin(appView, stateToAdd.asMonomorphic());
                });
          }
        });
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
}
