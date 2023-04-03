// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import static com.android.tools.r8.TestBase.toDexType;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.optimize.enums.EnumDataMap;
import com.android.tools.r8.utils.DescriptorUtils;

public class EnumUnboxingInspector {

  private final DexItemFactory dexItemFactory;
  private final EnumDataMap unboxedEnums;

  public EnumUnboxingInspector(DexItemFactory dexItemFactory, EnumDataMap unboxedEnums) {
    this.dexItemFactory = dexItemFactory;
    this.unboxedEnums = unboxedEnums;
  }

  public EnumUnboxingInspector assertNoEnumsUnboxed() {
    assertTrue(unboxedEnums.isEmpty());
    return this;
  }

  public EnumUnboxingInspector assertUnboxed(String typeName) {
    assertTrue(
        unboxedEnums.isUnboxedEnum(
            dexItemFactory.createType(DescriptorUtils.javaTypeToDescriptor(typeName))));
    return this;
  }

  public EnumUnboxingInspector assertUnboxed(Class<? extends Enum<?>> clazz) {
    assertTrue(clazz.getTypeName(), unboxedEnums.isUnboxedEnum(toDexType(clazz, dexItemFactory)));
    return this;
  }

  @SafeVarargs
  public final EnumUnboxingInspector assertUnboxed(Class<? extends Enum<?>>... classes) {
    for (Class<? extends Enum<?>> clazz : classes) {
      assertUnboxed(clazz);
    }
    return this;
  }

  public EnumUnboxingInspector assertUnboxedIf(boolean condition, Class<? extends Enum<?>> clazz) {
    return assertUnboxedIf(condition, clazz.getTypeName());
  }

  public EnumUnboxingInspector assertUnboxedIf(boolean condition, String className) {
    if (condition) {
      assertUnboxed(className);
    } else {
      assertNotUnboxed(className);
    }
    return this;
  }

  @SafeVarargs
  public final EnumUnboxingInspector assertUnboxedIf(
      boolean condition, Class<? extends Enum<?>>... classes) {
    for (Class<? extends Enum<?>> clazz : classes) {
      assertUnboxedIf(condition, clazz);
    }
    return this;
  }

  public EnumUnboxingInspector assertNotUnboxed(Class<? extends Enum<?>> clazz) {
    assertFalse(clazz.getTypeName(), unboxedEnums.isUnboxedEnum(toDexType(clazz, dexItemFactory)));
    return this;
  }

  public EnumUnboxingInspector assertNotUnboxed(String typeName) {
    assertFalse(
        unboxedEnums.isUnboxedEnum(
            dexItemFactory.createType(DescriptorUtils.javaTypeToDescriptor(typeName))));
    return this;
  }

  @SafeVarargs
  public final EnumUnboxingInspector assertNotUnboxed(Class<? extends Enum<?>>... classes) {
    for (Class<? extends Enum<?>> clazz : classes) {
      assertNotUnboxed(clazz);
    }
    return this;
  }
}
