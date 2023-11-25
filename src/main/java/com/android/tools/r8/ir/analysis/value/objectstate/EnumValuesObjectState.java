// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value.objectstate;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ArrayUtils;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;

public class EnumValuesObjectState extends ObjectState {

  private final ObjectState[] state;
  // Contains information about the class of each enum instance.
  private final ObjectClassForOrdinal objectClassForOrdinal;

  public EnumValuesObjectState(ObjectState[] state, DexType[] valuesTypes) {
    assert state.length > 0;
    assert valuesTypes.length == state.length;
    assert Arrays.stream(state).noneMatch(Objects::isNull);
    this.state = state;
    this.objectClassForOrdinal = ObjectClassForOrdinal.create(valuesTypes);
  }

  EnumValuesObjectState(ObjectState[] state, ObjectClassForOrdinal objectClassForOrdinal) {
    this.state = state;
    this.objectClassForOrdinal = objectClassForOrdinal;
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

  public DexType getObjectClassForOrdinal(int ordinal) {
    if (ordinal < 0 || ordinal >= state.length) {
      return null;
    }
    return objectClassForOrdinal.getObjectClassForOrdinal(ordinal);
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
    if (objectClassForOrdinal.enumHasBeenUnboxed(appView)) {
      // It does not make sense to keep outdated data on unboxed enums.
      // We have the exact content of the array but this is not modeled at this point so we simply
      // return a KnownLengthArrayState.
      return appView.abstractValueFactory().createKnownLengthArrayState(state.length);
    }
    ObjectState[] newState = new ObjectState[state.length];
    for (int i = 0; i < state.length; i++) {
      newState[i] = state[i].rewrittenWithLens(appView, lens, codeLens);
    }
    return new EnumValuesObjectState(
        newState, objectClassForOrdinal.rewrittenWithLens(appView, lens, codeLens));
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object o) {
    if (getClass() != o.getClass()) {
      return false;
    }
    EnumValuesObjectState other = (EnumValuesObjectState) o;
    if (state.length != other.state.length) {
      return false;
    }
    if (!Arrays.equals(state, other.state)) {
      return false;
    }
    return objectClassForOrdinal.equals(other.objectClassForOrdinal);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(state);
  }

  abstract static class ObjectClassForOrdinal {

    static ObjectClassForOrdinal create(DexType[] valuesClass) {
      return sameType(valuesClass)
          ? new UniformObjectClassForOrdinal(valuesClass[0])
          : new VariableObjectClassForOrdinal(valuesClass);
    }

    @SuppressWarnings("ReferenceEquality")
    static boolean sameType(DexType[] valuesClass) {
      DexType defaultType = valuesClass[0];
      for (DexType type : valuesClass) {
        if (type != defaultType) {
          return false;
        }
      }
      return true;
    }

    abstract DexType getObjectClassForOrdinal(int ordinal);

    abstract ObjectClassForOrdinal rewrittenWithLens(
        AppView<AppInfoWithLiveness> appView, GraphLens lens, GraphLens codeLens);

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

    public abstract boolean enumHasBeenUnboxed(AppView<?> appView);
  }

  static class UniformObjectClassForOrdinal extends ObjectClassForOrdinal {
    private final DexType type;

    UniformObjectClassForOrdinal(DexType type) {
      assert type != null;
      this.type = type;
    }

    @Override
    DexType getObjectClassForOrdinal(int ordinal) {
      return type;
    }

    @Override
    ObjectClassForOrdinal rewrittenWithLens(
        AppView<AppInfoWithLiveness> appView, GraphLens lens, GraphLens codeLens) {
      DexType rewrittenType = lens.lookupType(type, codeLens);
      assert rewrittenType.isClassType();
      return new UniformObjectClassForOrdinal(rewrittenType);
    }

    @Override
    public int hashCode() {
      return type.hashCode();
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean equals(Object obj) {
      if (!(obj instanceof UniformObjectClassForOrdinal)) {
        return false;
      }
      UniformObjectClassForOrdinal other = (UniformObjectClassForOrdinal) obj;
      return type == other.type;
    }

    @Override
    public boolean enumHasBeenUnboxed(AppView<?> appView) {
      return appView.unboxedEnums().isUnboxedEnum(type);
    }
  }

  static class VariableObjectClassForOrdinal extends ObjectClassForOrdinal {
    private final DexType[] types;

    VariableObjectClassForOrdinal(DexType[] types) {
      assert Arrays.stream(types).noneMatch(Objects::isNull);
      this.types = types;
    }

    @Override
    DexType getObjectClassForOrdinal(int ordinal) {
      assert ordinal >= 0 && ordinal < types.length;
      return types[ordinal];
    }

    @Override
    ObjectClassForOrdinal rewrittenWithLens(
        AppView<AppInfoWithLiveness> appView, GraphLens lens, GraphLens codeLens) {
      DexType[] newTypes =
          ArrayUtils.map(
              types,
              type -> {
                DexType rewrittenType = lens.lookupType(type, codeLens);
                assert rewrittenType.isClassType();
                return rewrittenType;
              },
              DexType.EMPTY_ARRAY);
      return create(newTypes);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(types);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof VariableObjectClassForOrdinal)) {
        return false;
      }
      VariableObjectClassForOrdinal other = (VariableObjectClassForOrdinal) obj;
      return Arrays.equals(types, other.types);
    }

    @Override
    public boolean enumHasBeenUnboxed(AppView<?> appView) {
      assert types.length > 0;
      return appView.unboxedEnums().isUnboxedEnum(types[0]);
    }
  }
}
