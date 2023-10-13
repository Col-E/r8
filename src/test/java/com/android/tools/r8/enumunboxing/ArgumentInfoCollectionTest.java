// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.graph.proto.RemovedArgumentInfo;
import com.android.tools.r8.graph.proto.RewrittenTypeInfo;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.utils.InternalOptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArgumentInfoCollectionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testCombineRewritten() {
    DexItemFactory factory = new DexItemFactory();
    ArgumentInfoCollection.Builder builder1 = ArgumentInfoCollection.builder();
    builder1.addArgumentInfo(
        1,
        RewrittenTypeInfo.builder()
            .setOldType(factory.intType)
            .setNewType(factory.longType)
            .build());
    builder1.addArgumentInfo(
        3,
        RewrittenTypeInfo.builder()
            .setOldType(factory.intType)
            .setNewType(factory.longType)
            .build());
    ArgumentInfoCollection arguments1 = builder1.setArgumentInfosSize(5).build();

    ArgumentInfoCollection.Builder builder2 = ArgumentInfoCollection.builder();
    builder2.addArgumentInfo(
        2,
        RewrittenTypeInfo.builder()
            .setOldType(factory.floatType)
            .setNewType(factory.doubleType)
            .build());
    builder2.addArgumentInfo(
        4,
        RewrittenTypeInfo.builder()
            .setOldType(factory.floatType)
            .setNewType(factory.doubleType)
            .build());
    ArgumentInfoCollection arguments2 = builder2.setArgumentInfosSize(5).build();

    ArgumentInfoCollection combine = arguments1.combine(arguments2);
    assertEquals(5, combine.size());

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
        1,
        RemovedArgumentInfo.builder()
            .setType(factory.intType)
            .build());
    builder1.addArgumentInfo(
        3,
        RemovedArgumentInfo.builder()
            .setType(factory.intType)
            .build());
    ArgumentInfoCollection arguments1 = builder1.setArgumentInfosSize(5).build();

    // Arguments removed: 0 2 4 -> 0. Arguments 2 and 4 are at position 1 and 2 after first removal.
    ArgumentInfoCollection.Builder builder2 = ArgumentInfoCollection.builder();
    builder2.addArgumentInfo(1, RemovedArgumentInfo.builder().setType(factory.doubleType).build());
    builder2.addArgumentInfo(2, RemovedArgumentInfo.builder().setType(factory.doubleType).build());
    ArgumentInfoCollection arguments2 = builder2.setArgumentInfosSize(3).build();

    // Arguments removed: 0 1 2 3 4 -> 0.
    ArgumentInfoCollection combine = arguments1.combine(arguments2);
    assertEquals(5, combine.size());

    RemovedArgumentInfo arg1 = combine.getArgumentInfo(1).asRemovedArgumentInfo();
    assertEquals(arg1.getType(), factory.intType);
    assertFalse(arg1.hasSingleValue());
    RemovedArgumentInfo arg2 = combine.getArgumentInfo(2).asRemovedArgumentInfo();
    assertEquals(arg2.getType(), factory.doubleType);
    assertFalse(arg2.hasSingleValue());
    RemovedArgumentInfo arg3 = combine.getArgumentInfo(3).asRemovedArgumentInfo();
    assertEquals(arg3.getType(), factory.intType);
    assertFalse(arg3.hasSingleValue());
    RemovedArgumentInfo arg4 = combine.getArgumentInfo(4).asRemovedArgumentInfo();
    assertEquals(arg4.getType(), factory.doubleType);
    assertFalse(arg4.hasSingleValue());
  }

  @Test
  public void testCombineRemoveRewritten() {
    InternalOptions options = new InternalOptions();
    DexItemFactory factory = options.dexItemFactory();
    AbstractValueFactory abstractValueFactory = new AbstractValueFactory(options);

    ArgumentInfoCollection.Builder builder1 = ArgumentInfoCollection.builder();
    builder1.addArgumentInfo(
        1,
        RemovedArgumentInfo.builder()
            .setType(factory.intType)
            .setSingleValue(abstractValueFactory.createZeroValue())
            .build());
    builder1.addArgumentInfo(
        3,
        RemovedArgumentInfo.builder()
            .setType(factory.intType)
            .setSingleValue(abstractValueFactory.createZeroValue())
            .build());
    ArgumentInfoCollection arguments1 = builder1.setArgumentInfosSize(5).build();

    ArgumentInfoCollection.Builder builder2 = ArgumentInfoCollection.builder();
    builder2.addArgumentInfo(
        1,
        RewrittenTypeInfo.builder()
            .setOldType(factory.floatType)
            .setNewType(factory.doubleType)
            .build());
    builder2.addArgumentInfo(
        2,
        RewrittenTypeInfo.builder()
            .setOldType(factory.floatType)
            .setNewType(factory.doubleType)
            .build());
    ArgumentInfoCollection arguments2 = builder2.setArgumentInfosSize(3).build();

    ArgumentInfoCollection combine = arguments1.combine(arguments2);
    assertEquals(5, combine.size());

    RemovedArgumentInfo arg1 = combine.getArgumentInfo(1).asRemovedArgumentInfo();
    assertEquals(arg1.getType(), factory.intType);
    assertTrue(arg1.hasSingleValue());
    RewrittenTypeInfo arg2 = combine.getArgumentInfo(2).asRewrittenTypeInfo();
    assertEquals(arg2.getOldType(), factory.floatType);
    assertEquals(arg2.getNewType(), factory.doubleType);
    RemovedArgumentInfo arg3 = combine.getArgumentInfo(3).asRemovedArgumentInfo();
    assertEquals(arg3.getType(), factory.intType);
    assertTrue(arg3.hasSingleValue());
    RewrittenTypeInfo arg4 = combine.getArgumentInfo(4).asRewrittenTypeInfo();
    assertEquals(arg4.getOldType(), factory.floatType);
    assertEquals(arg4.getNewType(), factory.doubleType);
  }
}
