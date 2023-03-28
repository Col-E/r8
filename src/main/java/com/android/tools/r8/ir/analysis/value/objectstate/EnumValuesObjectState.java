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
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;

public class EnumValuesObjectState extends ObjectState {

  private final ObjectState[] state;

  public EnumValuesObjectState(ObjectState[] state) {
    assert state.length > 0;
    assert Arrays.stream(state).noneMatch(Objects::isNull);
    this.state = state;
  }

  @Override
  public void forEachAbstractFieldValue(BiConsumer<DexField, AbstractValue> consumer) {}

  @Override
  public AbstractValue getAbstractFieldValue(DexEncodedField field) {
    return UnknownValue.getInstance();
  }

  public ObjectState getObjectStateForOrdinal(int ordinal) {
    if (ordinal < 0 || ordinal >= state.length) {
      return ObjectState.empty();
    }
    return state[ordinal];
  }

  public int getEnumValuesSize() {
    return state.length;
  }

  @Override
  public boolean hasKnownArrayLength() {
    return true;
  }

  @Override
  public int getKnownArrayLength() {
    return state.length;
  }

  @Override
  public boolean isEnumValuesObjectState() {
    return true;
  }

  @Override
  public EnumValuesObjectState asEnumValuesObjectState() {
    return this;
  }

  @Override
  public boolean isEmpty() {
    // Non-empty by construction.
    return false;
  }

  @Override
  public ObjectState rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens lens, GraphLens codeLens) {
    ObjectState[] newState = new ObjectState[state.length];
    for (int i = 0; i < state.length; i++) {
      newState[i] = state[i].rewrittenWithLens(appView, lens, codeLens);
    }
    return new EnumValuesObjectState(newState);
  }

  @Override
  public boolean equals(Object o) {
    if (getClass() != o.getClass()) {
      return false;
    }
    EnumValuesObjectState other = (EnumValuesObjectState) o;
    if (state.length != other.state.length) {
      return false;
    }
    for (int i = 0; i < state.length; i++) {
      if (!state[i].equals(other.state[i])) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(state);
  }
}
