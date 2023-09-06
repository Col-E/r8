// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value.objectstate;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class NonEmptyObjectState extends ObjectState {

  private final Map<DexField, AbstractValue> state;

  /** Intentionally package private, use {@link ObjectState.Builder}. */
  NonEmptyObjectState(Map<DexField, AbstractValue> state) {
    assert !state.isEmpty();
    assert state.values().stream().noneMatch(AbstractValue::isUnknown);
    this.state = state;
  }

  @Override
  public void forEachAbstractFieldValue(BiConsumer<DexField, AbstractValue> consumer) {
    state.forEach(consumer);
  }

  @Override
  public AbstractValue getAbstractFieldValue(DexEncodedField field) {
    return state.getOrDefault(field.getReference(), UnknownValue.getInstance());
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public ObjectState rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens lens, GraphLens codeLens) {
    Map<DexField, AbstractValue> rewrittenState = new IdentityHashMap<>();
    state.forEach(
        (field, value) ->
            rewrittenState.put(
                lens.lookupField(field, codeLens),
                value.rewrittenWithLens(appView, lens, codeLens)));
    return new NonEmptyObjectState(rewrittenState);
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
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
