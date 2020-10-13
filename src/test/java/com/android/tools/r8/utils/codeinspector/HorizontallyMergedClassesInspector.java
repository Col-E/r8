// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import static com.android.tools.r8.TestBase.toDexType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.horizontalclassmerging.HorizontallyMergedClasses;

public class HorizontallyMergedClassesInspector {

  private final DexItemFactory dexItemFactory;
  private final HorizontallyMergedClasses horizontallyMergedClasses;

  public HorizontallyMergedClassesInspector(
      DexItemFactory dexItemFactory, HorizontallyMergedClasses horizontallyMergedClasses) {
    this.dexItemFactory = dexItemFactory;
    this.horizontallyMergedClasses = horizontallyMergedClasses;
  }

  public HorizontallyMergedClassesInspector assertMergedInto(Class<?> from, Class<?> target) {
    assertEquals(
        horizontallyMergedClasses.getMergeTargetOrDefault(toDexType(from, dexItemFactory)),
        toDexType(target, dexItemFactory));
    return this;
  }

  public HorizontallyMergedClassesInspector assertNoClassesMerged() {
    assertTrue(horizontallyMergedClasses.getSources().isEmpty());
    return this;
  }

  public HorizontallyMergedClassesInspector assertClassNotMerged(Class<?> clazz) {
    assertFalse(horizontallyMergedClasses.hasBeenMerged(toDexType(clazz, dexItemFactory)));
    return this;
  }

  public HorizontallyMergedClassesInspector assertClassNotMergedIntoDifferentType(Class<?> clazz) {
    assertFalse(
        horizontallyMergedClasses.hasBeenMergedIntoDifferentType(toDexType(clazz, dexItemFactory)));
    return this;
  }

  public HorizontallyMergedClassesInspector assertMerged(Class<?> clazz) {
    assertTrue(
        horizontallyMergedClasses.hasBeenMergedOrIsMergeTarget(toDexType(clazz, dexItemFactory)));
    return this;
  }

  public HorizontallyMergedClassesInspector assertMerged(Class<?>... classes) {
    for (Class<?> clazz : classes) {
      assertMerged(clazz);
    }
    return this;
  }

  public HorizontallyMergedClassesInspector assertMergedIntoDifferentType(Class<?> clazz) {
    assertTrue(
        horizontallyMergedClasses.hasBeenMergedIntoDifferentType(toDexType(clazz, dexItemFactory)));
    return this;
  }

  public HorizontallyMergedClassesInspector assertMergedIntoDifferentType(Class<?>... classes) {
    for (Class<?> clazz : classes) {
      assertMergedIntoDifferentType(clazz);
    }
    return this;
  }
}
