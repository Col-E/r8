// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SegmentTreeTests extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public SegmentTreeTests(TestParameters parameters) {
    this.parameters = parameters;
  }

  private SegmentTree<Integer> tree(boolean allowOverrides) {
    return new SegmentTree<>(allowOverrides);
  }

  @Test
  public void testInsertSimpleRange() {
    SegmentTree<Integer> tree = tree(false).add(1, 100, 3);
    Map.Entry<Integer, Integer> entry = tree.findEntry(50);
    assertEntry(tree, 50, 1, 3);
    assertNull(tree.find(0));
    assertNull(tree.find(101));
  }

  @Test
  public void testInsertMultipleRanges() {
    SegmentTree<Integer> tree = tree(false).add(1, 10, 3).add(20, 30, 7).add(31, 100, 9);
    assertEntry(tree, 10, 1, 3);
    assertNull(tree.findEntry(11));
    assertEntry(tree, 20, 20, 7);
    assertEquals(tree.findEntry(31), tree.findEntry(100));
    assertNull(tree.find(101));
  }

  @Test(expected = AssertionError.class)
  public void testAssertingNoOverrides() {
    tree(false).add(1, 10, 3).add(7, 2, 4);
  }

  @Test
  public void testOverrideLeft() {
    SegmentTree<Integer> tree = tree(true).add(7, 9, 2).add(6, 7, 1);
    assertNull(tree.findEntry(5));
    assertEntry(tree, 7, 6, 1);
    assertEntry(tree, 9, 8, 2);
    assertNull(tree.findEntry(10));
    assertEquals(2, tree.size());
  }

  @Test
  public void testOverrideRight() {
    SegmentTree<Integer> tree = tree(true).add(6, 7, 1).add(7, 9, 2);
    assertNull(tree.findEntry(5));
    assertEntry(tree, 6, 6, 1);
    assertEntry(tree, 8, 7, 2);
    assertNull(tree.findEntry(10));
    assertEquals(2, tree.size());
  }

  @Test
  public void testOverrideContained() {
    SegmentTree<Integer> tree = tree(true).add(0, 100, 1).add(25, 75, 2);
    assertEntry(tree, 24, 0, 1);
    assertEntry(tree, 75, 25, 2);
    assertEntry(tree, 100, 76, 1);
    assertNull(tree.findEntry(101));
    assertEquals(2, tree.size());
  }

  @Test
  public void testOverrideContainer() {
    SegmentTree<Integer> tree = tree(true).add(25, 75, 2).add(0, 100, 1);
    assertEntry(tree, 24, 0, 1);
    assertEntry(tree, 75, 0, 1);
    assertEntry(tree, 100, 0, 1);
    assertNull(tree.findEntry(101));
    assertEquals(1, tree.size());
  }

  private void assertEntry(
      SegmentTree<Integer> tree, int index, int expectedStart, int expectedValue) {
    assertEquals(expectedStart, (int) tree.findEntry(index).getKey());
    assertEquals(expectedValue, (int) tree.findEntry(index).getValue());
  }
}
