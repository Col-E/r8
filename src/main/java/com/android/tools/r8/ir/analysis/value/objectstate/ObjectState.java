// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value.objectstate;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public abstract class ObjectState {

  public static Builder builder() {
    return new Builder();
  }

  public static ObjectState empty() {
    return EmptyObjectState.getInstance();
  }

  public abstract void forEachAbstractFieldValue(BiConsumer<DexField, AbstractValue> consumer);

  public final boolean hasMaterializableFieldValueThatMatches(
      AppView<AppInfoWithLiveness> appView,
      DexEncodedField field,
      ProgramMethod context,
      Predicate<SingleValue> predicate) {
    AbstractValue abstractValue = getAbstractFieldValue(field);
    if (!abstractValue.isSingleValue()) {
      return false;
    }
    SingleValue singleValue = abstractValue.asSingleValue();
    if (!singleValue.isMaterializableInContext(appView, context)) {
      return false;
    }
    return predicate.test(singleValue);
  }

  public abstract AbstractValue getAbstractFieldValue(DexEncodedField field);

  public abstract boolean isEmpty();

  public abstract ObjectState rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens lens, GraphLens codeLens);

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();

  public boolean hasKnownArrayLength() {
    return false;
  }

  public int getKnownArrayLength() {
    // Override this method if hasKnownArrayLength answers true.
    throw new Unreachable();
  }

  public boolean isEnumValuesObjectState() {
    return false;
  }

  public EnumValuesObjectState asEnumValuesObjectState() {
    return null;
  }

  public static class Builder {

    private final Map<DexField, AbstractValue> state = new IdentityHashMap<>();

    public void recordFieldHasValue(DexClassAndField field, AbstractValue abstractValue) {
      if (!abstractValue.isUnknown()) {
        assert !state.containsKey(field.getReference());
        state.put(field.getReference(), abstractValue);
      }
    }

    public ObjectState build() {
      return state.isEmpty() ? empty() : new NonEmptyObjectState(state);
    }
  }
}
