// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.LookupResult.LookupResultSuccess.LookupResultCollectionState;
import java.util.Collections;
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

  public final void forEach(Consumer<LookupTarget> onTarget) {
    forEach(onTarget::accept, onTarget::accept);
  }

  public abstract void forEach(
      Consumer<DexClassAndMethod> onMethodTarget, Consumer<LookupLambdaTarget> onLambdaTarget);

  public static LookupResultSuccess createResult(
      Map<DexEncodedMethod, DexClassAndMethod> methodTargets,
      List<LookupLambdaTarget> lambdaTargets,
      LookupResultCollectionState state) {
    return new LookupResultSuccess(methodTargets, lambdaTargets, state);
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
            Collections.emptyMap(),
            Collections.emptyList(),
            LookupResultCollectionState.Incomplete);

    private final Map<DexEncodedMethod, DexClassAndMethod> methodTargets;
    private final List<LookupLambdaTarget> lambdaTargets;
    private LookupResultCollectionState state;

    private LookupResultSuccess(
        Map<DexEncodedMethod, DexClassAndMethod> methodTargets,
        List<LookupLambdaTarget> lambdaTargets,
        LookupResultCollectionState state) {
      this.methodTargets = methodTargets;
      this.lambdaTargets = lambdaTargets;
      this.state = state;
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
        Consumer<DexClassAndMethod> onMethodTarget, Consumer<LookupLambdaTarget> onLambdaTarget) {
      methodTargets.forEach((ignore, method) -> onMethodTarget.accept(method));
      lambdaTargets.forEach(onLambdaTarget);
    }

    public boolean contains(DexEncodedMethod method) {
      // Containment of a method in the lookup results only pertains to the method targets.
      return methodTargets.containsKey(method);
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
        Consumer<DexClassAndMethod> onMethodTarget, Consumer<LookupLambdaTarget> onLambdaTarget) {
      // Nothing to iterate for a failed lookup.
    }
  }
}
