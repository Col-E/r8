// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestBase.MinifyMode;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.dexinspector.ClassSubject;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShakingRepeatedprotoTest extends TreeShakingTest {

  @Parameters(name = "mode:{0}-{1} minify:{2}")
  public static Collection<Object[]> data() {
    List<Object[]> parameters = new ArrayList<>();
    for (MinifyMode minify : MinifyMode.values()) {
      parameters.add(new Object[] {Frontend.JAR, Backend.CF, minify});
      parameters.add(new Object[] {Frontend.JAR, Backend.DEX, minify});
      parameters.add(new Object[] {Frontend.DEX, Backend.DEX, minify});
    }
    return parameters;
  }

  public TreeShakingRepeatedprotoTest(Frontend frontend, Backend backend, MinifyMode minify) {
    super("examples/repeatedproto", "repeatedproto.Repeatedproto", frontend, backend, minify);
  }

  @Test
  public void test() throws Exception {
    runTest(
        TreeShakingRepeatedprotoTest::repeatedUnusedFieldsAreGone,
        null,
        null,
        ImmutableList.of("src/test/examples/repeatedproto/keep-rules.txt"));
  }

  private static void repeatedUnusedFieldsAreGone(DexInspector inspector) {
    ClassSubject protoClass = inspector.clazz("repeatedproto.GeneratedRepeatedProto$Repeated");
    Assert.assertTrue(protoClass.isPresent());
    Assert.assertFalse(protoClass.field("int", "id_").isPresent());
    Assert.assertTrue(
        protoClass.field("com.google.protobuf.Internal$ProtobufList", "repeated_").isPresent());
    Assert.assertFalse(
        protoClass.field("com.google.protobuf.Internal$ProtobufList", "sub_").isPresent());
    Assert.assertFalse(
        protoClass.field("com.google.protobuf.Internal$BooleanList", "other_").isPresent());
  }
}
