// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.LookupResult.LookupResultSuccess.LookupResultCollectionState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class LookupResult {

  public boolean isLookupResultFailure() {
    return false;
  }

  public boolean isLookupResultSuccess() {
    return false;
  }

  public LookupResultFailure asLookupResultFailure() {
    return null;
  }

  public LookupResultSuccess asLookupResultSuccess() {
    return null;
  }

  public final void forEach(Consumer<? super LookupTarget> onTarget) {
    forEach(onTarget, onTarget);
  }

  public abstract void forEach(
      Consumer<? super LookupMethodTarget> onMethodTarget,
      Consumer<? super LookupLambdaTarget> onLambdaTarget);

  public abstract void forEachFailureDependency(
      Consumer<? super DexEncodedMethod> methodCausingFailureConsumer);

  public static LookupResultSuccess createResult(
      Map<DexMethod, LookupMethodTarget> methodTargets,
      List<LookupLambdaTarget> lambdaTargets,
      List<DexEncodedMethod> methodsCausingFailure,
      LookupResultCollectionState state) {
    return new LookupResultSuccess(methodTargets, lambdaTargets, methodsCausingFailure, state);
  }

  public static LookupResultFailure createFailedResult() {
    return LookupResultFailure.INSTANCE;
  }

  public static LookupResultSuccess getIncompleteEmptyResult() {
    return LookupResultSuccess.EMPTY_INSTANCE;
  }

  public static class LookupResultSuccess extends LookupResult {

    private static final LookupResultSuccess EMPTY_INSTANCE =
        new LookupResultSuccess(
            new IdentityHashMap<>(),
            Collections.emptyList(),
            Collections.emptyList(),
            LookupResultCollectionState.Incomplete);

    private final Map<DexMethod, LookupMethodTarget> methodTargets;
    private final List<LookupLambdaTarget> lambdaTargets;
    private final List<DexEncodedMethod> methodsCausingFailure;
    private LookupResultCollectionState state;

    private LookupResultSuccess(
        Map<DexMethod, LookupMethodTarget> methodTargets,
        List<LookupLambdaTarget> lambdaTargets,
        List<DexEncodedMethod> methodsCausingFailure,
        LookupResultCollectionState state) {
      this.methodTargets = methodTargets;
      this.lambdaTargets = lambdaTargets;
      this.methodsCausingFailure = methodsCausingFailure;
      this.state = state;
    }

    public static Builder builder() {
      return new Builder();
    }

    public boolean isEmpty() {
      return methodTargets.isEmpty() && lambdaTargets.isEmpty();
    }

    public boolean hasMethodTargets() {
      return !methodTargets.isEmpty();
    }

    public boolean hasLambdaTargets() {
      return !lambdaTargets.isEmpty();
    }

    @Override
    public void forEach(
        Consumer<? super LookupMethodTarget> onMethodTarget,
        Consumer<? super LookupLambdaTarget> onLambdaTarget) {
      methodTargets.forEach((key, value) -> onMethodTarget.accept(value));
      lambdaTargets.forEach(onLambdaTarget);
    }

    @Override
    public void forEachFailureDependency(
        Consumer<? super DexEncodedMethod> methodCausingFailureConsumer) {
      methodsCausingFailure.forEach(methodCausingFailureConsumer);
    }

    public boolean contains(DexEncodedMethod method) {
      // Containment of a method in the lookup results only pertains to the method targets.
      return methodTargets.containsKey(method.getReference());
    }

    @Override
    public LookupResultSuccess asLookupResultSuccess() {
      return this;
    }

    @Override
    public boolean isLookupResultSuccess() {
      return true;
    }

    public boolean isIncomplete() {
      return state == LookupResultCollectionState.Incomplete;
    }

    public boolean isComplete() {
      return state == LookupResultCollectionState.Complete;
    }

    public void setIncomplete() {
      // TODO(b/148769279): Remove when we have instantiated info.
      state = LookupResultCollectionState.Incomplete;
    }

    public LookupTarget getSingleLookupTarget() {
      if (isIncomplete() || methodTargets.size() + lambdaTargets.size() > 1) {
        return null;
      }
      // TODO(b/150932978): Check lambda targets implementation methods.
      if (methodTargets.size() == 1) {
        return methodTargets.values().iterator().next();
      } else if (lambdaTargets.size() == 1) {
        return lambdaTargets.get(0);
      }
      return null;
    }

    public enum LookupResultCollectionState {
      Complete,
      Incomplete,
    }

    public static class Builder {

      private final Map<DexMethod, LookupMethodTarget> methodTargets = new IdentityHashMap<>();
      private final List<LookupLambdaTarget> lambdaTargets = new ArrayList<>();
      private final List<DexEncodedMethod> methodsCausingFailure = new ArrayList<>();
      private LookupResultCollectionState state;

      public Builder addMethodTarget(LookupMethodTarget methodTarget) {
        assert methodTarget.isMethodTarget();
        methodTargets.putIfAbsent(methodTarget.asMethodTarget().getReference(), methodTarget);
        return this;
      }

      public Builder addLambdaTarget(LookupLambdaTarget lambdaTarget) {
        lambdaTargets.add(lambdaTarget);
        return this;
      }

      public Builder addMethodCausingFailure(DexEncodedMethod methodCausingFailure) {
        methodsCausingFailure.add(methodCausingFailure);
        return this;
      }

      public Builder setState(LookupResultCollectionState state) {
        this.state = state;
        return this;
      }

      public LookupResultSuccess build() {
        return new LookupResultSuccess(methodTargets, lambdaTargets, methodsCausingFailure, state);
      }
    }
  }

  public static class LookupResultFailure extends LookupResult {

    private static final LookupResultFailure INSTANCE = new LookupResultFailure();

    private LookupResultFailure() {
      // Empty to only allow creation locally.
    }

    @Override
    public LookupResultFailure asLookupResultFailure() {
      return this;
    }

    @Override
    public boolean isLookupResultFailure() {
      return true;
    }

    @Override
    public void forEach(
        Consumer<? super LookupMethodTarget> onMethodTarget,
        Consumer<? super LookupLambdaTarget> onLambdaTarget) {
      // Nothing to iterate for a failed lookup.
    }

    @Override
    public void forEachFailureDependency(
        Consumer<? super DexEncodedMethod> methodCausingFailureConsumer) {
      // TODO: record and emit failure dependencies.
    }
  }
}
