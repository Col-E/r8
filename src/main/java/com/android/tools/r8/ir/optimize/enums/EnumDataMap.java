// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import static com.android.tools.r8.ir.optimize.enums.EnumUnboxerImpl.ordinalToUnboxedInt;

import com.android.tools.r8.errors.CheckEnumUnboxedDiagnostic;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.optimize.enums.EnumInstanceFieldData.EnumInstanceFieldKnownData;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class EnumDataMap {
  private final ImmutableMap<DexType, EnumData> map;
  private final ImmutableMap<DexType, DexType> subEnumToSuperEnumMap;

  public static EnumDataMap empty() {
    return new EnumDataMap(ImmutableMap.of(), ImmutableMap.of());
  }

  public EnumDataMap(
      ImmutableMap<DexType, EnumData> map, ImmutableMap<DexType, DexType> subEnumToSuperEnumMap) {
    this.map = map;
    this.subEnumToSuperEnumMap = subEnumToSuperEnumMap;
  }

  public boolean hasAnyEnumsWithSubtypes() {
    return !subEnumToSuperEnumMap.isEmpty();
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

  @SuppressWarnings("ReferenceEquality")
  public boolean isAssignableTo(DexType subtype, DexType superType) {
    assert superType != null;
    assert subtype != null;
    if (superType == subtype) {
      return true;
    }
    return superType == subEnumToSuperEnumMap.get(subtype);
  }

  public DexType representativeType(DexType type) {
    return subEnumToSuperEnumMap.getOrDefault(type, type);
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public boolean isSuperUnboxedEnum(DexType type) {
    return map.containsKey(type);
  }

  public SingleNumberValue getSingleNumberValueFromEnumType(
      AbstractValueFactory factory, DexType enumType) {
    assert isUnboxedEnum(enumType);
    EnumData enumData = get(enumType);
    return isSuperUnboxedEnum(enumType)
        ? enumData.superEnumTypeSingleValue(factory)
        : enumData.subEnumTypeSingleValue(factory, enumType);
  }

  public boolean isUnboxedEnum(DexProgramClass clazz) {
    return isUnboxedEnum(clazz.getType());
  }

  public boolean isUnboxedEnum(DexType type) {
    return map.containsKey(representativeType(type));
  }

  public EnumData get(DexType type) {
    EnumData enumData = map.get(representativeType(type));
    assert enumData != null;
    return enumData;
  }

  public EnumData get(DexProgramClass enumClass) {
    return get(enumClass.getType());
  }

  public Set<DexType> getUnboxedSuperEnums() {
    return map.keySet();
  }

  public Set<DexType> computeAllUnboxedEnums() {
    Set<DexType> items = Sets.newIdentityHashSet();
    items.addAll(map.keySet());
    items.addAll(subEnumToSuperEnumMap.keySet());
    return items;
  }

  public EnumInstanceFieldKnownData getInstanceFieldData(
      DexType enumType, DexField enumInstanceField) {
    assert isUnboxedEnum(enumType);
    return get(enumType).getInstanceFieldData(enumInstanceField);
  }

  public boolean hasUnboxedValueFor(DexField enumStaticField) {
    DexType representative = representativeType(enumStaticField.getHolderType());
    return isSuperUnboxedEnum(representative)
        && get(representative).hasUnboxedValueFor(enumStaticField);
  }

  public int getUnboxedValue(DexField enumStaticField) {
    assert isUnboxedEnum(enumStaticField.getHolderType());
    return get(representativeType(enumStaticField.getHolderType()))
        .getUnboxedValue(enumStaticField);
  }

  public int getValuesSize(DexType enumType) {
    assert isSuperUnboxedEnum(enumType);
    return get(enumType).getValuesSize();
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
    assert isSuperUnboxedEnum(staticField.getHolderType());
    return get(staticField.getHolderType()).matchesValuesField(staticField);
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

    public SingleNumberValue superEnumTypeSingleValue(AbstractValueFactory factory) {
      // If there is a single live enum instance, then return the unboxed value for this one.
      if (hasValues()) {
        if (valuesSize == 1) {
          return factory.createSingleNumberValue(ordinalToUnboxedInt(0), TypeElement.getInt());
        }
      } else if (unboxedValues.size() == 1) {
        Integer next = unboxedValues.values().iterator().next();
        return factory.createSingleNumberValue(ordinalToUnboxedInt(next), TypeElement.getInt());
      }
      return null;
    }

    @SuppressWarnings("ReferenceEquality")
    public SingleNumberValue subEnumTypeSingleValue(AbstractValueFactory factory, DexType type) {
      assert valuesTypes.values().stream().filter(t -> t == type).count() <= 1;
      for (Entry<DexType> entry : valuesTypes.int2ReferenceEntrySet()) {
        if (entry.getValue() == type) {
          return factory.createSingleNumberValue(
              ordinalToUnboxedInt(entry.getIntKey()), TypeElement.getInt());
        }
      }
      return null;
    }
  }
}
