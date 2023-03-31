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
public class MediaDrmBackportTest extends AbstractBackportTest {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  public MediaDrmBackportTest(TestParameters parameters) throws IOException {
    super(
        parameters,
        MediaDrmBackportTest.getMediaDrm(parameters),
        ImmutableList.of(
            MediaDrmBackportTest.getTestRunner(), MediaDrmBackportTest.getMediaDrm(parameters)));

    // The constructor is used by the test and release has been available since API 18 and is the
    // method close is rewritten to.
    ignoreInvokes("<init>");
    ignoreInvokes("release");

    // android.media.MediaDrm.close added in API 28.
    registerTarget(AndroidApiLevel.P, 1);
  }

  private static byte[] getMediaDrm(TestParameters parameters) throws IOException {
    if (parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.P)) {
      assertTrue(parameters.getRuntime().asDex().getVm().isNewerThanOrEqual(DexVm.ART_8_1_0_HOST));
      return transformer(MediaDrmApiLevel28.class)
          .setClassDescriptor(DexItemFactory.androidMediaMediaDrmDescriptorString)
          .transform();
    } else {
      return transformer(MediaDrm.class)
          .setClassDescriptor(DexItemFactory.androidMediaMediaDrmDescriptorString)
          .transform();
    }
  }

  private static byte[] getTestRunner() throws IOException {
    return transformer(TestRunner.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(MediaDrm.class), DexItemFactory.androidMediaMediaDrmDescriptorString)
        .transform();
  }

  public static class MediaDrm {
    public boolean wasClosed = false;

    public void close() {
      TestRunner.doFail("close should not be called");
    }

    public void release() {
      wasClosed = true;
    }
  }

  public static class MediaDrmApiLevel28 {
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
      MediaDrm mediaDrm = new MediaDrm();
      MiniAssert.assertFalse(mediaDrm.wasClosed);
      mediaDrm.close();
      MiniAssert.assertTrue(mediaDrm.wasClosed);
    }

    // Forwards to MiniAssert to avoid having to make it public.
    public static void doFail(String message) {
      MiniAssert.fail(message);
    }
  }
}
