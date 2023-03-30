// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.errors.CheckEnumUnboxedDiagnostic;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.optimize.enums.EnumInstanceFieldData.EnumInstanceFieldKnownData;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class EnumDataMap {
  private final ImmutableMap<DexType, EnumData> map;

  public static EnumDataMap empty() {
    return new EnumDataMap(ImmutableMap.of());
  }

  public EnumDataMap(ImmutableMap<DexType, EnumData> map) {
    this.map = map;
  }

  public void checkEnumsUnboxed(AppView<AppInfoWithLiveness> appView) {
    List<DexProgramClass> failed = new ArrayList<>();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.isEnum()) {
        if (appView.getKeepInfo(clazz).isCheckEnumUnboxedEnabled(appView.options())
            && !isUnboxedEnum(clazz)) {
          failed.add(clazz);
        }
      }
    }
    if (!failed.isEmpty()) {
      CheckEnumUnboxedDiagnostic diagnostic =
          CheckEnumUnboxedDiagnostic.builder().addFailedEnums(failed).build();
      throw appView.reporter().fatalError(diagnostic);
    }
  }

  public boolean isUnboxedEnum(DexProgramClass clazz) {
    return isUnboxedEnum(clazz.getType());
  }

  public boolean isUnboxedEnum(DexType type) {
    return map.containsKey(type);
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public EnumData get(DexProgramClass enumClass) {
    EnumData enumData = map.get(enumClass.getType());
    assert enumData != null;
    return enumData;
  }

  public Set<DexType> getUnboxedEnums() {
    return map.keySet();
  }

  public EnumInstanceFieldKnownData getInstanceFieldData(
      DexType enumType, DexField enumInstanceField) {
    assert map.containsKey(enumType);
    return map.get(enumType).getInstanceFieldData(enumInstanceField);
  }

  public boolean hasUnboxedValueFor(DexField enumStaticField) {
    return isUnboxedEnum(enumStaticField.holder)
        && map.get(enumStaticField.holder).hasUnboxedValueFor(enumStaticField);
  }

  public int getUnboxedValue(DexField enumStaticField) {
    assert map.containsKey(enumStaticField.holder);
    return map.get(enumStaticField.holder).getUnboxedValue(enumStaticField);
  }

  public int getValuesSize(DexType enumType) {
    assert map.containsKey(enumType);
    return map.get(enumType).getValuesSize();
  }

  public int getMaxValuesSize() {
    int maxValuesSize = 0;
    for (EnumData data : map.values()) {
      if (data.hasValues()) {
        maxValuesSize = Math.max(maxValuesSize, data.getValuesSize());
      }
    }
    return maxValuesSize;
  }

  public boolean matchesValuesField(DexField staticField) {
    assert map.containsKey(staticField.holder);
    return map.get(staticField.holder).matchesValuesField(staticField);
  }

  public static class EnumData {
    static final int INVALID_VALUES_SIZE = -1;

    // Map each enum instance field to the list of field known data.
    final ImmutableMap<DexField, EnumInstanceFieldKnownData> instanceFieldMap;
    // Map each ordinal to their original type. This is recorded *only* if the enum has subtypes.
    final Int2ReferenceMap<DexType> valuesTypes;
    // Map each enum instance (static field) to the unboxed integer value.
    final ImmutableMap<DexField, Integer> unboxedValues;
    // Fields matching the $VALUES content and type, usually one.
    final ImmutableSet<DexField> valuesFields;
    // Size of the $VALUES field, if the valuesFields set is empty, set to INVALID_VALUES_SIZE.
    final int valuesSize;

    public EnumData(
        ImmutableMap<DexField, EnumInstanceFieldKnownData> instanceFieldMap,
        Int2ReferenceMap<DexType> valuesTypes,
        ImmutableMap<DexField, Integer> unboxedValues,
        ImmutableSet<DexField> valuesFields,
        int valuesSize) {
      this.instanceFieldMap = instanceFieldMap;
      this.valuesTypes = valuesTypes;
      this.unboxedValues = unboxedValues;
      this.valuesFields = valuesFields;
      this.valuesSize = valuesSize;
    }

    public EnumInstanceFieldKnownData getInstanceFieldData(DexField enumInstanceField) {
      assert instanceFieldMap.containsKey(enumInstanceField);
      return instanceFieldMap.get(enumInstanceField);
    }

    public int getUnboxedValue(DexField field) {
      assert unboxedValues.containsKey(field);
      return unboxedValues.get(field);
    }

    public boolean hasUnboxedValueFor(ProgramField field) {
      return hasUnboxedValueFor(field.getReference());
    }

    public boolean hasUnboxedValueFor(DexField field) {
      return unboxedValues.containsKey(field);
    }

    public boolean matchesValuesField(ProgramField field) {
      return matchesValuesField(field.getReference());
    }

    public boolean matchesValuesField(DexField field) {
      return valuesFields.contains(field);
    }

    public boolean hasValues() {
      return valuesSize != INVALID_VALUES_SIZE;
    }

    public int getValuesSize() {
      assert hasValues();
      return valuesSize;
    }
  }
}
