// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.ImmutableMap;

public abstract class StaticFieldValues {

  public boolean isEnumStaticFieldValues() {
    return false;
  }

  public EnumStaticFieldValues asEnumStaticFieldValues() {
    return null;
  }

  public static Builder builder(DexProgramClass clazz) {
    return clazz.isEnum() ? EnumStaticFieldValues.builder() : EmptyStaticValues.builder();
  }

  public abstract static class Builder {

    public abstract void recordStaticField(
        DexClassAndField staticField, AbstractValue value, DexItemFactory factory);

    public abstract StaticFieldValues build();
  }

  // All the abstract values stored here may match a pinned field, using them requires therefore
  // to check the field is not pinned or prove it is no longer pinned.
  public static class EnumStaticFieldValues extends StaticFieldValues {
    private final ImmutableMap<DexField, ObjectState> enumAbstractValues;

    public EnumStaticFieldValues(ImmutableMap<DexField, ObjectState> enumAbstractValues) {
      this.enumAbstractValues = enumAbstractValues;
    }

    static StaticFieldValues.Builder builder() {
      return new Builder();
    }

    public EnumStaticFieldValues rewrittenWithLens(
        AppView<AppInfoWithLiveness> appView, GraphLens lens, GraphLens codeLens) {
      ImmutableMap.Builder<DexField, ObjectState> builder = ImmutableMap.builder();
      enumAbstractValues.forEach(
          (field, state) ->
              builder.put(
                  lens.lookupField(field), state.rewrittenWithLens(appView, lens, codeLens)));
      return new EnumStaticFieldValues(builder.build());
    }

    public static class Builder extends StaticFieldValues.Builder {
      private final ImmutableMap.Builder<DexField, ObjectState> enumObjectStateBuilder =
          ImmutableMap.builder();
      private AbstractValue valuesCandidateAbstractValue;

      Builder() {}

      @Override
      public void recordStaticField(
          DexClassAndField staticField, AbstractValue value, DexItemFactory factory) {
        if (factory.enumMembers.isValuesFieldCandidate(staticField, staticField.getHolderType())) {
          if (value.isSingleFieldValue()
              && value.asSingleFieldValue().getObjectState().isEnumValuesObjectState()) {
            assert valuesCandidateAbstractValue == null
                || valuesCandidateAbstractValue.equals(value);
            valuesCandidateAbstractValue = value;
            enumObjectStateBuilder.put(
                staticField.getReference(), value.asSingleFieldValue().getObjectState());
          }
        } else if (factory.enumMembers.isEnumFieldCandidate(staticField)) {
          if (value.isSingleFieldValue()
              && !value.asSingleFieldValue().getObjectState().isEmpty()) {
            enumObjectStateBuilder.put(
                staticField.getReference(), value.asSingleFieldValue().getObjectState());
          }
        }
      }

      @Override
      public StaticFieldValues build() {
        ImmutableMap<DexField, ObjectState> enumAbstractValues = enumObjectStateBuilder.build();
        if (enumAbstractValues.isEmpty()) {
          return EmptyStaticValues.getInstance();
        }
        assert enumAbstractValues.values().stream().noneMatch(ObjectState::isEmpty);
        return new EnumStaticFieldValues(enumAbstractValues);
      }
    }

    @Override
    public boolean isEnumStaticFieldValues() {
      return true;
    }

    @Override
    public EnumStaticFieldValues asEnumStaticFieldValues() {
      return this;
    }

    public ObjectState getObjectStateForPossiblyPinnedField(DexField field) {
      return enumAbstractValues.get(field);
    }
  }

  public static class EmptyStaticValues extends StaticFieldValues {
    private static EmptyStaticValues INSTANCE = new EmptyStaticValues();

    private EmptyStaticValues() {}

    public static EmptyStaticValues getInstance() {
      return INSTANCE;
    }

    static StaticFieldValues.Builder builder() {
      return new Builder();
    }

    public static class Builder extends StaticFieldValues.Builder {

      @Override
      public void recordStaticField(
          DexClassAndField staticField, AbstractValue value, DexItemFactory factory) {
        // Do nothing.
      }

      @Override
      public StaticFieldValues build() {
        return EmptyStaticValues.getInstance();
      }
    }
  }
}
