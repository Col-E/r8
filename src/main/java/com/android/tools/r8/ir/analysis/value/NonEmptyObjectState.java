// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.GraphLens;
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
  public AbstractValue getAbstractFieldValue(DexEncodedField field) {
    return state.getOrDefault(field.field, UnknownValue.getInstance());
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public ObjectState rewrittenWithLens(AppView<AppInfoWithLiveness> appView, GraphLens lens) {
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
    if (state.size() != other.state.size()) {
      return false;
    }
    for (DexField dexField : state.keySet()) {
      AbstractValue localValue = state.get(dexField);
      AbstractValue otherValue = other.state.get(dexField);
      if (!localValue.equals(otherValue)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    return state.hashCode();
  }
}
