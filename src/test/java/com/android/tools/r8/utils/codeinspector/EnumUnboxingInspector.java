// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import static com.android.tools.r8.TestBase.toDexType;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.EnumValueInfoMapCollection;

public class EnumUnboxingInspector {

  private final DexItemFactory dexItemFactory;
  private final EnumValueInfoMapCollection unboxedEnums;

  public EnumUnboxingInspector(
      DexItemFactory dexItemFactory, EnumValueInfoMapCollection unboxedEnums) {
    this.dexItemFactory = dexItemFactory;
    this.unboxedEnums = unboxedEnums;
  }

  public EnumUnboxingInspector assertUnboxed(Class<? extends Enum<?>> clazz) {
    assertTrue(unboxedEnums.containsEnum(toDexType(clazz, dexItemFactory)));
    return this;
  }

  @SafeVarargs
  public final EnumUnboxingInspector assertUnboxed(Class<? extends Enum<?>>... classes) {
    for (Class<? extends Enum<?>> clazz : classes) {
      assertUnboxed(clazz);
    }
    return this;
  }
}
