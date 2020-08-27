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

public abstract class ObjectState {

  public static Builder builder() {
    return new Builder();
  }

  public static ObjectState empty() {
    return EmptyObjectState.getInstance();
  }

  public abstract AbstractValue getAbstractFieldValue(DexEncodedField field);

  public abstract boolean isEmpty();

  public abstract ObjectState rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens lens);

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();

  public boolean isEnumValuesObjectState() {
    return false;
  }

  public EnumValuesObjectState asEnumValuesObjectState() {
    return null;
  }

  public static class Builder {

    private final Map<DexField, AbstractValue> state = new IdentityHashMap<>();

    public void recordFieldHasValue(DexEncodedField field, AbstractValue abstractValue) {
      if (!abstractValue.isUnknown()) {
        assert !state.containsKey(field.field);
        state.put(field.field, abstractValue);
      }
    }

    public ObjectState build() {
      return state.isEmpty() ? empty() : new NonEmptyObjectState(state);
    }
  }
}
