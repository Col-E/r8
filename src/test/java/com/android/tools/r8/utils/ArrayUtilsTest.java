// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import java.util.Map;
import org.junit.Test;

public class ArrayUtilsTest {

  @Test
  public void testCopyWithSparseChanges_identical() throws Exception {
    int size = 3;
    Integer[] input = new Integer[size];
    for (int i = 0; i < size; i++) {
      input[i] = i;
    }
    Integer[] output =
        ArrayUtils.copyWithSparseChanges(Integer[].class, input, new Int2IntArrayMap());
    assertNotEquals(input, output);
    for (int i = 0; i < size; i++) {
      assertTrue(i == output[i]);
    }
  }

  @Test
  public void testCopyWithSparseChanges_oneChangeAtBeginning() throws Exception {
    int size = 3;
    Integer[] input = new Integer[size];
    for (int i = 0; i < size; i++) {
      input[i] = i;
    }
    Map<Integer, Integer> changes = new Int2IntArrayMap();
    changes.put(0, size);
    Integer[] output = ArrayUtils.copyWithSparseChanges(Integer[].class, input, changes);
    assertNotEquals(input, output);
    assertTrue(size == output[0]);
    for (int i = 1; i < size; i++) {
      assertTrue(i == output[i]);
    }
  }

  @Test
  public void testCopyWithSparseChanges_oneChangeInMiddle() throws Exception {
    int size = 3;
    Integer[] input = new Integer[size];
    for (int i = 0; i < size; i++) {
      input[i] = i;
    }
    Map<Integer, Integer> changes = new Int2IntArrayMap();
    changes.put(1, size);
    Integer[] output = ArrayUtils.copyWithSparseChanges(Integer[].class, input, changes);
    assertNotEquals(input, output);
    assertTrue(size == output[1]);
    for (int i = 0; i < size; i++) {
      if (i == 1) {
        continue;
      }
      assertTrue(i == output[i]);
    }
  }

  @Test
  public void testCopyWithSparseChanges_oneChangeAtEnd() throws Exception {
    int size = 3;
    Integer[] input = new Integer[size];
    for (int i = 0; i < size; i++) {
      input[i] = i;
    }
    Map<Integer, Integer> changes = new Int2IntArrayMap();
    changes.put(2, size);
    Integer[] output = ArrayUtils.copyWithSparseChanges(Integer[].class, input, changes);
    assertNotEquals(input, output);
    assertTrue(size == output[2]);
    for (int i = 0; i < size - 1; i++) {
      assertTrue(i == output[i]);
    }
  }

  @Test
  public void testCopyWithSparseChanges_twoChangesAtEnds() throws Exception {
    int size = 3;
    Integer[] input = new Integer[size];
    for (int i = 0; i < size; i++) {
      input[i] = i;
    }
    Map<Integer, Integer> changes = new Int2IntArrayMap();
    changes.put(0, size);
    changes.put(2, size);
    Integer[] output = ArrayUtils.copyWithSparseChanges(Integer[].class, input, changes);
    assertNotEquals(input, output);
    assertTrue(size == output[0]);
    assertFalse(size == output[1]);
    assertTrue(size == output[2]);
  }

}
