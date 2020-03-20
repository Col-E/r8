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

public class TypeElementWidthTest extends TestBase {

  @Test
  public void testArrayWidth() {
    ArrayTypeElement arrayType =
        ArrayTypeElement.create(TypeElement.getInt(), Nullability.maybeNull());
    assertFalse(arrayType.isSinglePrimitive());
    assertFalse(arrayType.isWidePrimitive());
    assertEquals(1, arrayType.requiredRegisters());
  }

  @Test
  public void testBooleanWidth() {
    assertTrue(TypeElement.getBoolean().isSinglePrimitive());
    assertFalse(TypeElement.getBoolean().isWidePrimitive());
    assertEquals(1, TypeElement.getBoolean().requiredRegisters());
  }

  @Test
  public void testByteWidth() {
    assertTrue(TypeElement.getByte().isSinglePrimitive());
    assertFalse(TypeElement.getByte().isWidePrimitive());
    assertEquals(1, TypeElement.getByte().requiredRegisters());
  }

  @Test
  public void testCharWidth() {
    assertTrue(TypeElement.getChar().isSinglePrimitive());
    assertFalse(TypeElement.getChar().isWidePrimitive());
    assertEquals(1, TypeElement.getChar().requiredRegisters());
  }

  @Test
  public void testDoubleWidth() {
    assertTrue(TypeElement.getDouble().isWidePrimitive());
    assertFalse(TypeElement.getDouble().isSinglePrimitive());
    assertEquals(2, TypeElement.getDouble().requiredRegisters());
  }

  @Test
  public void testFloatWidth() {
    assertTrue(TypeElement.getFloat().isSinglePrimitive());
    assertFalse(TypeElement.getFloat().isWidePrimitive());
    assertEquals(1, TypeElement.getFloat().requiredRegisters());
  }

  @Test
  public void testIntWidth() {
    assertTrue(TypeElement.getInt().isSinglePrimitive());
    assertFalse(TypeElement.getInt().isWidePrimitive());
    assertEquals(1, TypeElement.getInt().requiredRegisters());
  }

  @Test
  public void testLongWidth() {
    assertTrue(TypeElement.getLong().isWidePrimitive());
    assertFalse(TypeElement.getLong().isSinglePrimitive());
    assertEquals(2, TypeElement.getLong().requiredRegisters());
  }

  @Test
  public void testReferenceWidth() {
    DexItemFactory dexItemFactory = new DexItemFactory();
    ClassTypeElement referenceType =
        ClassTypeElement.create(
            dexItemFactory.objectType, Nullability.maybeNull(), ImmutableSet.of());
    assertFalse(referenceType.isSinglePrimitive());
    assertFalse(referenceType.isWidePrimitive());
    assertEquals(1, referenceType.requiredRegisters());
  }

  @Test
  public void testShortWidth() {
    assertTrue(TypeElement.getShort().isSinglePrimitive());
    assertFalse(TypeElement.getShort().isWidePrimitive());
    assertEquals(1, TypeElement.getShort().requiredRegisters());
  }
}
