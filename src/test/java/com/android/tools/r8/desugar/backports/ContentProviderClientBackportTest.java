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
public class ContentProviderClientBackportTest extends AbstractBackportTest {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  public ContentProviderClientBackportTest(TestParameters parameters) throws IOException {
    super(
        parameters,
        ContentProviderClientBackportTest.getContentProviderClient(parameters),
        ImmutableList.of(
            ContentProviderClientBackportTest.getTestRunner(),
            ContentProviderClientBackportTest.getContentProviderClient(parameters)));

    // The constructor is used by the test and release has been available since API 5 and is the
    // method close is rewritten to.
    ignoreInvokes("<init>");
    ignoreInvokes("release");

    // android.content.ContentProviderClient.close added in API 24.
    registerTarget(AndroidApiLevel.N, 1);
  }

  private static byte[] getContentProviderClient(TestParameters parameters) throws IOException {
    if (parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N)) {
      assertTrue(parameters.getRuntime().asDex().getVm().isNewerThanOrEqual(DexVm.ART_7_0_0_HOST));
      return transformer(ContentProviderClientApiLevel24.class)
          .setClassDescriptor(DexItemFactory.androidContentContentProviderClientDescriptorString)
          .transform();
    } else {
      return transformer(ContentProviderClient.class)
          .setClassDescriptor(DexItemFactory.androidContentContentProviderClientDescriptorString)
          .transform();
    }
  }

  private static byte[] getTestRunner() throws IOException {
    return transformer(TestRunner.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(ContentProviderClient.class),
            DexItemFactory.androidContentContentProviderClientDescriptorString)
        .transform();
  }

  public static class ContentProviderClient {
    public boolean wasClosed = false;

    public void close() {
      TestRunner.doFail("close should not be called");
    }

    public void release() {
      wasClosed = true;
    }
  }

  public static class ContentProviderClientApiLevel24 {
    public boolean wasClosed = false;

    public void close() {
      wasClosed = true;
    }

    public void release() {
      TestRunner.doFail("release should not be called");
    }
  }

  public static class TestRunner extends MiniAssert {

    public static void main(String[] args) {
      ContentProviderClient contentProviderClient = new ContentProviderClient();
      MiniAssert.assertFalse(contentProviderClient.wasClosed);
      contentProviderClient.close();
      MiniAssert.assertTrue(contentProviderClient.wasClosed);
    }

    // Forwards to MiniAssert to avoid having to make it public.
    public static void doFail(String message) {
      MiniAssert.fail(message);
    }
  }
}
