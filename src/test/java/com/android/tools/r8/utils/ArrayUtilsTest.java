// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.Test;

public class ArrayUtilsTest {

  private Integer[] createInputData(int size) {
    Integer[] input = new Integer[size];
    for (int i = 0; i < size; i++) {
      input[i] = i;
    }
    return input;
  }

  @Test
  public void testCopyWithSparseChanges_identical() {
    int size = 3;
    Integer[] input = createInputData(size);
    Integer[] output =
        ArrayUtils.copyWithSparseChanges(Integer[].class, input, new Int2IntArrayMap());
    assertNotEquals(input, output);
    for (int i = 0; i < size; i++) {
      assertEquals(i, (int) output[i]);
    }
  }

  @Test
  public void testCopyWithSparseChanges_oneChangeAtBeginning() {
    int size = 3;
    Integer[] input = createInputData(size);
    Map<Integer, Integer> changes = new Int2IntArrayMap();
    changes.put(0, size);
    Integer[] output = ArrayUtils.copyWithSparseChanges(Integer[].class, input, changes);
    assertNotEquals(input, output);
    assertEquals(size, (int) output[0]);
    for (int i = 1; i < size; i++) {
      assertEquals(i, (int) output[i]);
    }
  }

  @Test
  public void testCopyWithSparseChanges_oneChangeInMiddle() {
    int size = 3;
    Integer[] input = createInputData(size);
    Map<Integer, Integer> changes = new Int2IntArrayMap();
    changes.put(1, size);
    Integer[] output = ArrayUtils.copyWithSparseChanges(Integer[].class, input, changes);
    assertNotEquals(input, output);
    assertEquals(size, (int) output[1]);
    for (int i = 0; i < size; i++) {
      if (i == 1) {
        continue;
      }
      assertEquals(i, (int) output[i]);
    }
  }

  @Test
  public void testCopyWithSparseChanges_oneChangeAtEnd() {
    int size = 3;
    Integer[] input = createInputData(size);
    Map<Integer, Integer> changes = new Int2IntArrayMap();
    changes.put(2, size);
    Integer[] output = ArrayUtils.copyWithSparseChanges(Integer[].class, input, changes);
    assertNotEquals(input, output);
    assertEquals(size, (int) output[2]);
    for (int i = 0; i < size - 1; i++) {
      assertEquals(i, (int) output[i]);
    }
  }

  @Test
  public void testCopyWithSparseChanges_twoChangesAtEnds() {
    int size = 3;
    Integer[] input = createInputData(size);
    Map<Integer, Integer> changes = new Int2IntArrayMap();
    changes.put(0, size);
    changes.put(2, size);
    Integer[] output = ArrayUtils.copyWithSparseChanges(Integer[].class, input, changes);
    assertNotEquals(input, output);
    assertEquals(size, (int) output[0]);
    assertNotEquals(size, (int) output[1]);
    assertEquals(size, (int) output[2]);
  }

  @Test
  public void testFilter_identity() {
    int size = 3;
    Integer[] input = createInputData(size);
    Integer[] output = ArrayUtils.filter(Integer[].class, input, x -> true);
    assertEquals(input, output);
  }

  @Test
  public void testFilter_dropOdd() {
    int size = 3;
    Integer[] input = createInputData(size);
    Integer[] output = ArrayUtils.filter(Integer[].class, input, x -> x % 2 == 0);
    assertNotEquals(input, output);
    assertEquals(2, output.length);
    assertEquals(0, (int) output[0]);
    assertEquals(2, (int) output[1]);
  }

  @Test
  public void testFilter_dropAll() {
    int size = 3;
    Integer[] input = createInputData(size);
    Integer[] output = ArrayUtils.filter(Integer[].class, input, x -> false);
    assertNotEquals(input, output);
    assertEquals(0, output.length);
  }

  @Test
  public void testMap_identity() {
    int size = 3;
    Integer[] input = createInputData(size);
    Integer[] output = ArrayUtils.map(Integer[].class, input, Function.identity());
    assertEquals(input, output);
  }

  @Test
  public void testMap_dropOdd() {
    int size = 3;
    Integer[] input = createInputData(size);
    Integer[] output = ArrayUtils.map(Integer[].class, input, x -> x % 2 != 0 ? null : x);
    assertNotEquals(input, output);
    assertEquals(2, output.length);
    assertEquals(0, (int) output[0]);
    assertEquals(2, (int) output[1]);
  }

  @Test
  public void testMap_dropAll() {
    int size = 3;
    Integer[] input = createInputData(size);
    Integer[] output = ArrayUtils.map(Integer[].class, input, x -> null);
    assertNotEquals(input, output);
    assertEquals(0, output.length);
  }

  @Test
  public void testMap_double() {
    int size = 3;
    Integer[] input = createInputData(size);
    Integer[] output = ArrayUtils.map(Integer[].class, input, x -> 2 * x);
    assertNotEquals(input, output);
    assertEquals(size, output.length);
    for (int i = 0; i < size; i++) {
      assertEquals(i * 2, (int) output[i]);
    }
  }

  @Test
  public void testMap_double_onlyOdd() {
    int size = 3;
    Integer[] input = createInputData(size);
    Integer[] output = ArrayUtils.map(Integer[].class, input, x -> x % 2 != 0 ? 2 * x : x);
    assertNotEquals(input, output);
    assertEquals(size, output.length);
    for (int i = 0; i < size; i++) {
      if (i % 2 != 0) {
        assertEquals(i * 2, (int) output[i]);
      } else {
        assertEquals(i, (int) output[i]);
      }
    }
  }

}
