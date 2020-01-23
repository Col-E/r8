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
        ArrayTypeLatticeElement.create(TypeLatticeElement.getInt(), Nullability.maybeNull());
    assertFalse(arrayType.isSinglePrimitive());
    assertFalse(arrayType.isWidePrimitive());
    assertEquals(1, arrayType.requiredRegisters());
  }

  @Test
  public void testBooleanWidth() {
    assertTrue(TypeLatticeElement.getBoolean().isSinglePrimitive());
    assertFalse(TypeLatticeElement.getBoolean().isWidePrimitive());
    assertEquals(1, TypeLatticeElement.getBoolean().requiredRegisters());
  }

  @Test
  public void testByteWidth() {
    assertTrue(TypeLatticeElement.getByte().isSinglePrimitive());
    assertFalse(TypeLatticeElement.getByte().isWidePrimitive());
    assertEquals(1, TypeLatticeElement.getByte().requiredRegisters());
  }

  @Test
  public void testCharWidth() {
    assertTrue(TypeLatticeElement.getChar().isSinglePrimitive());
    assertFalse(TypeLatticeElement.getChar().isWidePrimitive());
    assertEquals(1, TypeLatticeElement.getChar().requiredRegisters());
  }

  @Test
  public void testDoubleWidth() {
    assertTrue(TypeLatticeElement.getDouble().isWidePrimitive());
    assertFalse(TypeLatticeElement.getDouble().isSinglePrimitive());
    assertEquals(2, TypeLatticeElement.getDouble().requiredRegisters());
  }

  @Test
  public void testFloatWidth() {
    assertTrue(TypeLatticeElement.getFloat().isSinglePrimitive());
    assertFalse(TypeLatticeElement.getFloat().isWidePrimitive());
    assertEquals(1, TypeLatticeElement.getFloat().requiredRegisters());
  }

  @Test
  public void testIntWidth() {
    assertTrue(TypeLatticeElement.getInt().isSinglePrimitive());
    assertFalse(TypeLatticeElement.getInt().isWidePrimitive());
    assertEquals(1, TypeLatticeElement.getInt().requiredRegisters());
  }

  @Test
  public void testLongWidth() {
    assertTrue(TypeLatticeElement.getLong().isWidePrimitive());
    assertFalse(TypeLatticeElement.getLong().isSinglePrimitive());
    assertEquals(2, TypeLatticeElement.getLong().requiredRegisters());
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
    assertTrue(TypeLatticeElement.getShort().isSinglePrimitive());
    assertFalse(TypeLatticeElement.getShort().isWidePrimitive());
    assertEquals(1, TypeLatticeElement.getShort().requiredRegisters());
  }
}
