// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.optimize.enums.EnumInstanceFieldData.EnumInstanceFieldKnownData;
import com.google.common.collect.ImmutableMap;

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
}
