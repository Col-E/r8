// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.ArgumentInfoCollection;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.RemovedArgumentInfo;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.RewrittenTypeInfo;
import org.junit.Test;

public class ArgumentInfoCollectionTest extends TestBase {

  @Test
  public void testCombineRewritten() {
    DexItemFactory factory = new DexItemFactory();
    ArgumentInfoCollection.Builder builder1 = ArgumentInfoCollection.builder();
    builder1.addArgumentInfo(1, new RewrittenTypeInfo(factory.intType, factory.longType));
    builder1.addArgumentInfo(3, new RewrittenTypeInfo(factory.intType, factory.longType));
    ArgumentInfoCollection arguments1 = builder1.build();

    ArgumentInfoCollection.Builder builder2 = ArgumentInfoCollection.builder();
    builder2.addArgumentInfo(2, new RewrittenTypeInfo(factory.floatType, factory.doubleType));
    builder2.addArgumentInfo(4, new RewrittenTypeInfo(factory.floatType, factory.doubleType));
    ArgumentInfoCollection arguments2 = builder2.build();

    ArgumentInfoCollection combine = arguments1.combine(arguments2);

    RewrittenTypeInfo arg1 = combine.getArgumentInfo(1).asRewrittenTypeInfo();
    assertEquals(arg1.getOldType(), factory.intType);
    assertEquals(arg1.getNewType(), factory.longType);
    RewrittenTypeInfo arg2 = combine.getArgumentInfo(2).asRewrittenTypeInfo();
    assertEquals(arg2.getOldType(), factory.floatType);
    assertEquals(arg2.getNewType(), factory.doubleType);
    RewrittenTypeInfo arg3 = combine.getArgumentInfo(3).asRewrittenTypeInfo();
    assertEquals(arg3.getOldType(), factory.intType);
    assertEquals(arg3.getNewType(), factory.longType);
    RewrittenTypeInfo arg4 = combine.getArgumentInfo(4).asRewrittenTypeInfo();
    assertEquals(arg4.getOldType(), factory.floatType);
    assertEquals(arg4.getNewType(), factory.doubleType);
  }

  @Test
  public void testCombineRemoved() {
    DexItemFactory factory = new DexItemFactory();

    // Arguments removed: 0 1 2 3 4 -> 0 2 4.
    ArgumentInfoCollection.Builder builder1 = ArgumentInfoCollection.builder();
    builder1.addArgumentInfo(
        1, RemovedArgumentInfo.builder().setType(factory.intType).setIsAlwaysNull().build());
    builder1.addArgumentInfo(
        3, RemovedArgumentInfo.builder().setType(factory.intType).setIsAlwaysNull().build());
    ArgumentInfoCollection arguments1 = builder1.build();

    // Arguments removed: 0 2 4 -> 0. Arguments 2 and 4 are at position 1 and 2 after first removal.
    ArgumentInfoCollection.Builder builder2 = ArgumentInfoCollection.builder();
    builder2.addArgumentInfo(1, RemovedArgumentInfo.builder().setType(factory.doubleType).build());
    builder2.addArgumentInfo(2, RemovedArgumentInfo.builder().setType(factory.doubleType).build());
    ArgumentInfoCollection arguments2 = builder2.build();

    // Arguments removed: 0 1 2 3 4 -> 0.
    ArgumentInfoCollection combine = arguments1.combine(arguments2);

    RemovedArgumentInfo arg1 = combine.getArgumentInfo(1).asRemovedArgumentInfo();
    assertEquals(arg1.getType(), factory.intType);
    assertTrue(arg1.isAlwaysNull());
    RemovedArgumentInfo arg2 = combine.getArgumentInfo(2).asRemovedArgumentInfo();
    assertEquals(arg2.getType(), factory.doubleType);
    assertFalse(arg2.isAlwaysNull());
    RemovedArgumentInfo arg3 = combine.getArgumentInfo(3).asRemovedArgumentInfo();
    assertEquals(arg3.getType(), factory.intType);
    assertTrue(arg3.isAlwaysNull());
    RemovedArgumentInfo arg4 = combine.getArgumentInfo(4).asRemovedArgumentInfo();
    assertEquals(arg4.getType(), factory.doubleType);
    assertFalse(arg4.isAlwaysNull());
  }

  @Test
  public void testCombineRemoveRewritten() {
    DexItemFactory factory = new DexItemFactory();

    ArgumentInfoCollection.Builder builder1 = ArgumentInfoCollection.builder();
    builder1.addArgumentInfo(
        1, RemovedArgumentInfo.builder().setType(factory.intType).setIsAlwaysNull().build());
    builder1.addArgumentInfo(
        3, RemovedArgumentInfo.builder().setType(factory.intType).setIsAlwaysNull().build());
    ArgumentInfoCollection arguments1 = builder1.build();

    ArgumentInfoCollection.Builder builder2 = ArgumentInfoCollection.builder();
    builder2.addArgumentInfo(1, new RewrittenTypeInfo(factory.floatType, factory.doubleType));
    builder2.addArgumentInfo(2, new RewrittenTypeInfo(factory.floatType, factory.doubleType));
    ArgumentInfoCollection arguments2 = builder2.build();

    ArgumentInfoCollection combine = arguments1.combine(arguments2);

    RemovedArgumentInfo arg1 = combine.getArgumentInfo(1).asRemovedArgumentInfo();
    assertEquals(arg1.getType(), factory.intType);
    assertTrue(arg1.isAlwaysNull());
    RewrittenTypeInfo arg2 = combine.getArgumentInfo(2).asRewrittenTypeInfo();
    assertEquals(arg2.getOldType(), factory.floatType);
    assertEquals(arg2.getNewType(), factory.doubleType);
    RemovedArgumentInfo arg3 = combine.getArgumentInfo(3).asRemovedArgumentInfo();
    assertEquals(arg3.getType(), factory.intType);
    assertTrue(arg3.isAlwaysNull());
    RewrittenTypeInfo arg4 = combine.getArgumentInfo(4).asRewrittenTypeInfo();
    assertEquals(arg4.getOldType(), factory.floatType);
    assertEquals(arg4.getNewType(), factory.doubleType);
  }
}
