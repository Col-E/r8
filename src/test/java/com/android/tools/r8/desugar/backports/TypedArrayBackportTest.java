// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TypedArrayBackportTest extends AbstractBackportTest {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  public TypedArrayBackportTest(TestParameters parameters) throws IOException {
    super(
        parameters,
        TypedArrayBackportTest.getTypedArray(parameters),
        ImmutableList.of(
            TypedArrayBackportTest.getTestRunner(),
            TypedArrayBackportTest.getTypedArray(parameters)));

    // The constructor is used by the test and recycle has been available since API 1 and is the
    // method close is rewritten to.
    ignoreInvokes("<init>");
    ignoreInvokes("recycle");

    // android.content.res.TypedArray.close added in API 31.
    registerTarget(AndroidApiLevel.S, 1);
  }

  private static byte[] getTypedArray(TestParameters parameters) throws IOException {
    if (parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.S)) {
      assertTrue(parameters.getRuntime().asDex().getVm().isNewerThanOrEqual(DexVm.ART_12_0_0_HOST));
      return transformer(TypedArrayAndroid12.class)
          .setClassDescriptor(DexItemFactory.androidContentResTypedArrayDescriptorString)
          .transform();
    } else {
      return transformer(TypedArray.class)
          .setClassDescriptor(DexItemFactory.androidContentResTypedArrayDescriptorString)
          .transform();
    }
  }

  private static byte[] getTestRunner() throws IOException {
    return transformer(TestRunner.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(TypedArray.class),
            DexItemFactory.androidContentResTypedArrayDescriptorString)
        .transform();
  }

  public static class TypedArray {
    public boolean wasClosed = false;

    public void close() {
      TestRunner.doFail("close should not be called");
    }

    public void recycle() {
      wasClosed = true;
    }
  }

  public static class TypedArrayAndroid12 {
    public boolean wasClosed = false;

    public void close() {
      wasClosed = true;
    }

    public void recycle() {
      TestRunner.doFail("recycle should not be called");
    }
  }

  public static class TestRunner extends MiniAssert {

    public static void main(String[] args) {
      TypedArray typedArray = new TypedArray();
      MiniAssert.assertFalse(typedArray.wasClosed);
      typedArray.close();
      MiniAssert.assertTrue(typedArray.wasClosed);
    }

    // Forwards to MiniAssert to avoid having to make it public.
    public static void doFail(String message) {
      MiniAssert.fail(message);
    }
  }
}
