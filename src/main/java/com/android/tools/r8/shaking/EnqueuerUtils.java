// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.PresortedComparable;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Predicate;

class EnqueuerUtils {

  static SortedSet<DexField> extractProgramFieldDefinitions(
      Set<DexField> instanceFields,
      Set<DexField> staticFields,
      AppInfo appInfo,
      Predicate<DexEncodedField> predicate) {
    return extractFieldDefinitions(
        instanceFields,
        staticFields,
        appInfo,
        field -> field.isProgramField(appInfo) && predicate.test(field));
  }

  static SortedSet<DexField> extractFieldDefinitions(
      Set<DexField> instanceFields,
      Set<DexField> staticFields,
      AppInfo appInfo,
      Predicate<DexEncodedField> predicate) {
    ImmutableSortedSet.Builder<DexField> builder =
        ImmutableSortedSet.orderedBy(PresortedComparable::slowCompareTo);
    for (DexField field : instanceFields) {
      DexEncodedField encodedField = appInfo.resolveField(field);
      if (encodedField != null && predicate.test(encodedField)) {
        builder.add(encodedField.field);
      }
    }
    for (DexField field : staticFields) {
      DexEncodedField encodedField = appInfo.resolveField(field);
      if (encodedField != null && predicate.test(encodedField)) {
        builder.add(encodedField.field);
      }
    }
    return builder.build();
  }

  static <T, U> ImmutableSortedMap<T, U> toImmutableSortedMap(
      Map<T, U> map, Comparator<T> comparator) {
    ImmutableSortedMap.Builder<T, U> builder = new ImmutableSortedMap.Builder<>(comparator);
    map.forEach(builder::put);
    return builder.build();
  }
}
