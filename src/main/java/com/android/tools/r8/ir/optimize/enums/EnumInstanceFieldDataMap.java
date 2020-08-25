// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.optimize.enums.EnumInstanceFieldData.EnumInstanceFieldKnownData;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class EnumInstanceFieldDataMap {
  private final ImmutableMap<DexType, ImmutableMap<DexField, EnumInstanceFieldKnownData>>
      instanceFieldMap;

  public EnumInstanceFieldDataMap(
      ImmutableMap<DexType, ImmutableMap<DexField, EnumInstanceFieldKnownData>> instanceFieldMap) {
    this.instanceFieldMap = instanceFieldMap;
  }

  public EnumInstanceFieldKnownData getInstanceFieldData(
      DexType enumType, DexField enumInstanceField) {
    assert instanceFieldMap.containsKey(enumType);
    assert instanceFieldMap.get(enumType).containsKey(enumInstanceField);
    return instanceFieldMap.get(enumType).get(enumInstanceField);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final Map<DexType, Map<DexField, EnumInstanceFieldData>> instanceFieldMap =
        new ConcurrentHashMap<>();

    public EnumInstanceFieldData computeInstanceFieldDataIfAbsent(
        DexType enumType,
        DexField enumInstanceField,
        Function<DexField, EnumInstanceFieldData> enumFieldDataSupplier) {
      Map<DexField, EnumInstanceFieldData> typeMap =
          instanceFieldMap.computeIfAbsent(enumType, ignored -> new ConcurrentHashMap<>());
      return typeMap.computeIfAbsent(enumInstanceField, enumFieldDataSupplier);
    }

    public EnumInstanceFieldDataMap build(ImmutableSet<DexType> enumsToUnbox) {
      ImmutableMap.Builder<DexType, ImmutableMap<DexField, EnumInstanceFieldKnownData>> builder =
          ImmutableMap.builder();
      instanceFieldMap.forEach(
          (enumType, typeMap) -> {
            ImmutableMap.Builder<DexField, EnumInstanceFieldKnownData> typeBuilder =
                ImmutableMap.builder();
            typeMap.forEach(
                (field, data) -> {
                  if (enumsToUnbox.contains(enumType)) {
                    assert data.isKnown();
                    typeBuilder.put(field, data.asEnumFieldKnownData());
                  }
                });
            builder.put(enumType, typeBuilder.build());
          });
      instanceFieldMap.clear();
      return new EnumInstanceFieldDataMap(builder.build());
    }
  }
}
