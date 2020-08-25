// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.google.common.collect.ImmutableMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class EnumValueInfoMapCollection {

  public static EnumValueInfoMapCollection empty() {
    return new EnumValueInfoMapCollection(ImmutableMap.of());
  }

  private final Map<DexType, EnumValueInfoMap> maps;

  private EnumValueInfoMapCollection(Map<DexType, EnumValueInfoMap> maps) {
    this.maps = maps;
  }

  public EnumValueInfoMap getEnumValueInfoMap(DexType type) {
    return maps.get(type);
  }

  public boolean isEmpty() {
    return maps.isEmpty();
  }

  public boolean containsEnum(DexType type) {
    return maps.containsKey(type);
  }

  public Set<DexType> enumSet() {
    return maps.keySet();
  }

  public EnumValueInfoMapCollection rewrittenWithLens(GraphLens lens) {
    Builder builder = builder();
    maps.forEach(
        (type, map) -> {
          DexType dexType = lens.lookupType(type);
          // Enum unboxing may have changed the type to int type.
          // Do not keep the map for such enums.
          if (!dexType.isPrimitiveType()) {
            builder.put(dexType, map.rewrittenWithLens(lens));
          }
        });
    return builder.build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private ImmutableMap.Builder<DexType, EnumValueInfoMap> builder;

    public Builder put(DexType type, EnumValueInfoMap map) {
      if (builder == null) {
        builder = ImmutableMap.builder();
      }
      builder.put(type, map);
      return this;
    }

    public EnumValueInfoMapCollection build() {
      if (builder == null) {
        return empty();
      }
      return new EnumValueInfoMapCollection(builder.build());
    }
  }

  public static final class EnumValueInfoMap {

    private final LinkedHashMap<DexField, EnumValueInfo> map;

    public EnumValueInfoMap(LinkedHashMap<DexField, EnumValueInfo> map) {
      this.map = map;
    }

    public Set<DexField> enumValues() {
      return map.keySet();
    }

    public int size() {
      return map.size();
    }

    public boolean hasEnumValueInfo(DexField field) {
      return map.containsKey(field);
    }

    public EnumValueInfo getEnumValueInfo(DexField field) {
      return map.get(field);
    }

    public void forEach(BiConsumer<DexField, EnumValueInfo> consumer) {
      map.forEach(consumer);
    }

    EnumValueInfoMap rewrittenWithLens(GraphLens lens) {
      LinkedHashMap<DexField, EnumValueInfo> rewritten = new LinkedHashMap<>();
      map.forEach(
          (field, valueInfo) ->
              rewritten.put(lens.lookupField(field), valueInfo.rewrittenWithLens(lens)));
      return new EnumValueInfoMap(rewritten);
    }
  }

  public static final class EnumValueInfo {

    // The anonymous subtype of this specific value or the enum type.
    public final DexType type;
    public final int ordinal;

    public EnumValueInfo(DexType type, int ordinal) {
      this.type = type;
      this.ordinal = ordinal;
    }

    public int convertToInt() {
      return ordinal + 1;
    }

    EnumValueInfo rewrittenWithLens(GraphLens lens) {
      DexType newType = lens.lookupType(type);
      if (type == newType) {
        return this;
      }
      return new EnumValueInfo(newType, ordinal);
    }
  }
}
