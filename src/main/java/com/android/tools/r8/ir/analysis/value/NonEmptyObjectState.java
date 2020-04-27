// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.IdentityHashMap;
import java.util.Map;

public class NonEmptyObjectState extends ObjectState {

  private final Map<DexField, AbstractValue> state;

  /** Intentionally package private, use {@link ObjectState.Builder}. */
  NonEmptyObjectState(Map<DexField, AbstractValue> state) {
    assert !state.isEmpty();
    assert state.values().stream().noneMatch(AbstractValue::isUnknown);
    this.state = state;
  }

  @Override
  public ObjectState rewrittenWithLens(AppView<AppInfoWithLiveness> appView, GraphLense lens) {
    Map<DexField, AbstractValue> rewrittenState = new IdentityHashMap<>();
    state.forEach(
        (field, value) ->
            rewrittenState.put(lens.lookupField(field), value.rewrittenWithLens(appView, lens)));
    return new NonEmptyObjectState(rewrittenState);
  }

  @Override
  public boolean equals(Object o) {
    if (getClass() != o.getClass()) {
      return false;
    }
    NonEmptyObjectState other = (NonEmptyObjectState) o;
    return state.equals(other.state);
  }

  @Override
  public int hashCode() {
    return state.hashCode();
  }
}
