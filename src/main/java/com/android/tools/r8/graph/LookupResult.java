// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.LookupResult.LookupResultSuccess.LookupResultCollectionState;
import java.util.Collections;
import java.util.Set;

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

  public static LookupResultSuccess createResult(
      Set<DexEncodedMethod> methodTargets, LookupResultCollectionState state) {
    return new LookupResultSuccess(methodTargets, state);
  }

  public static LookupResultFailure createFailedResult() {
    return LookupResultFailure.INSTANCE;
  }

  public static LookupResultSuccess getIncompleteEmptyResult() {
    return LookupResultSuccess.EMPTY_INSTANCE;
  }

  public static class LookupResultSuccess extends LookupResult {

    private static final LookupResultSuccess EMPTY_INSTANCE =
        new LookupResultSuccess(Collections.emptySet(), LookupResultCollectionState.Incomplete);

    private final Set<DexEncodedMethod> methodTargets;
    private final LookupResultCollectionState state;

    private LookupResultSuccess(
        Set<DexEncodedMethod> methodTargets, LookupResultCollectionState state) {
      this.methodTargets = methodTargets;
      this.state = state;
    }

    public boolean isEmpty() {
      return methodTargets == null || methodTargets.isEmpty();
    }

    public Set<DexEncodedMethod> getMethodTargets() {
      return methodTargets;
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
  }
}
