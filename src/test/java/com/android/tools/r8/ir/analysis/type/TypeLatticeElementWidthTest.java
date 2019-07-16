// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.DexItemFactory;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

public class TypeLatticeElementWidthTest extends TestBase {

  @Test
  public void testArrayWidth() {
    ArrayTypeLatticeElement arrayType =
        ArrayTypeLatticeElement.create(
            IntTypeLatticeElement.getInstance(), Nullability.maybeNull());
    assertFalse(arrayType.isSinglePrimitive());
    assertFalse(arrayType.isWidePrimitive());
    assertEquals(1, arrayType.requiredRegisters());
  }

  @Test
  public void testBooleanWidth() {
    assertTrue(BooleanTypeLatticeElement.getInstance().isSinglePrimitive());
    assertFalse(BooleanTypeLatticeElement.getInstance().isWidePrimitive());
    assertEquals(1, BooleanTypeLatticeElement.getInstance().requiredRegisters());
  }

  @Test
  public void testByteWidth() {
    assertTrue(ByteTypeLatticeElement.getInstance().isSinglePrimitive());
    assertFalse(ByteTypeLatticeElement.getInstance().isWidePrimitive());
    assertEquals(1, ByteTypeLatticeElement.getInstance().requiredRegisters());
  }

  @Test
  public void testCharWidth() {
    assertTrue(CharTypeLatticeElement.getInstance().isSinglePrimitive());
    assertFalse(CharTypeLatticeElement.getInstance().isWidePrimitive());
    assertEquals(1, CharTypeLatticeElement.getInstance().requiredRegisters());
  }

  @Test
  public void testDoubleWidth() {
    assertTrue(DoubleTypeLatticeElement.getInstance().isWidePrimitive());
    assertFalse(DoubleTypeLatticeElement.getInstance().isSinglePrimitive());
    assertEquals(2, DoubleTypeLatticeElement.getInstance().requiredRegisters());
  }

  @Test
  public void testFloatWidth() {
    assertTrue(FloatTypeLatticeElement.getInstance().isSinglePrimitive());
    assertFalse(FloatTypeLatticeElement.getInstance().isWidePrimitive());
    assertEquals(1, FloatTypeLatticeElement.getInstance().requiredRegisters());
  }

  @Test
  public void testIntWidth() {
    assertTrue(IntTypeLatticeElement.getInstance().isSinglePrimitive());
    assertFalse(IntTypeLatticeElement.getInstance().isWidePrimitive());
    assertEquals(1, IntTypeLatticeElement.getInstance().requiredRegisters());
  }

  @Test
  public void testLongWidth() {
    assertTrue(LongTypeLatticeElement.getInstance().isWidePrimitive());
    assertFalse(LongTypeLatticeElement.getInstance().isSinglePrimitive());
    assertEquals(2, LongTypeLatticeElement.getInstance().requiredRegisters());
  }

  @Test
  public void testReferenceWidth() {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ClassTypeLatticeElement referenceType =
        ClassTypeLatticeElement.create(
            dexItemFactory.objectType, Nullability.maybeNull(), ImmutableSet.of());
    assertFalse(referenceType.isSinglePrimitive());
    assertFalse(referenceType.isWidePrimitive());
    assertEquals(1, referenceType.requiredRegisters());
  }

  @Test
  public void testShortWidth() {
    assertTrue(ShortTypeLatticeElement.getInstance().isSinglePrimitive());
    assertFalse(ShortTypeLatticeElement.getInstance().isWidePrimitive());
    assertEquals(1, ShortTypeLatticeElement.getInstance().requiredRegisters());
  }
}
