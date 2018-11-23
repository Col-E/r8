// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b78493232;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.AndroidApp;
import org.junit.Test;

// Variant of Regress78493232, but where the new-instance is forced to flow to a non-trivial phi
// function prior to the call to <init>.
public class Regress78493232_WithPhi extends AsmTestBase {

  @Test
  public void test() throws Exception {
    // Run test on JVM and ART(x86) to ensure expected behavior.
    // Running the same test on an ARM JIT causes errors.

    // TODO(b/80118070): Remove this if-statement when fixed.
    if (ToolHelper.getDexVm().getVersion() != Version.V5_1_1) {
      AndroidApp app =
          buildAndroidApp(
              Regress78493232Dump_WithPhi.dump(),
              ToolHelper.getClassAsBytes(Regress78493232Utils.class));
      ProcessResult javaResult =
          runOnJavaRawNoVerify(
              Regress78493232Dump_WithPhi.CLASS_NAME,
              Regress78493232Dump_WithPhi.dump(),
              ToolHelper.getClassAsBytes(Regress78493232Utils.class));
      ProcessResult d8Result =
          runOnArtRaw(compileWithD8(app), Regress78493232Dump_WithPhi.CLASS_NAME);
      ProcessResult r8Result =
          runOnArtRaw(compileWithR8(app), Regress78493232Dump_WithPhi.CLASS_NAME);
      String proguardConfig =
          keepMainProguardConfiguration(Regress78493232Dump_WithPhi.CLASS_NAME)
              + "-dontobfuscate\n";
      ProcessResult r8ShakenResult =
          runOnArtRaw(compileWithR8(app, proguardConfig), Regress78493232Dump_WithPhi.CLASS_NAME);
      assertEquals(
          "After 0 iterations, expected \"java.security.SecureRandom\", but got \"null\"\n",
          javaResult.stdout);
      assertEquals(0, javaResult.exitCode);
      switch (ToolHelper.getDexVm().getVersion()) {
        case V4_0_4:
        case V4_4_4:
        case V7_0_0:
        case V8_1_0:
        case DEFAULT:
          assertNotEquals(-1, d8Result.stderr.indexOf("java.lang.VerifyError"));
          assertNotEquals(-1, r8Result.stderr.indexOf("java.lang.VerifyError"));
          assertNotEquals(-1, r8ShakenResult.stderr.indexOf("java.lang.VerifyError"));
          assertEquals(1, d8Result.exitCode);
          assertEquals(1, r8Result.exitCode);
          assertEquals(1, r8ShakenResult.exitCode);
          break;
        case V6_0_1:
          assertEquals("Completed successfully after 1000 iterations\n", d8Result.stdout);
          assertEquals("Completed successfully after 1000 iterations\n", r8Result.stdout);
          assertEquals("Completed successfully after 1000 iterations\n", r8ShakenResult.stdout);
          assertEquals(0, d8Result.exitCode);
          assertEquals(0, r8Result.exitCode);
          assertEquals(0, r8ShakenResult.exitCode);
          break;
        case V5_1_1:
        default:
          throw new Unreachable();
      }
      return;
    }
    ensureSameOutputJavaNoVerify(
        Regress78493232Dump_WithPhi.CLASS_NAME,
        Regress78493232Dump_WithPhi.dump(),
        ToolHelper.getClassAsBytes(Regress78493232Utils.class));
  }
}
