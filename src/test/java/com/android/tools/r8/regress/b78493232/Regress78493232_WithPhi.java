// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b78493232;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;

// Variant of Regress78493232, but where the new-instance is forced to flow to a non-trivial phi
// function prior to the call to <init>. Due to the non-trivial phi this JVM code will not pass
// the verifier. This test is kept to allow inspection of the code path hit in D8/R8 for such
// inputs, but besides that just documents the behaviour on the various VMs.
public class Regress78493232_WithPhi extends AsmTestBase {

  static final String expected =
      StringUtils.lines(
          "After 0 iterations, expected \"java.security.SecureRandom\", but got \"null\"");

  @Test
  public void test() throws Exception {
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
        runOnArtRaw(
            compileWithR8(app, "-dontshrink\n-dontobfuscate\n"),
            Regress78493232Dump_WithPhi.CLASS_NAME);
    String proguardConfig =
        keepMainProguardConfiguration(Regress78493232Dump_WithPhi.CLASS_NAME) + "-dontobfuscate\n";
    ProcessResult r8ShakenResult =
        runOnArtRaw(compileWithR8(app, proguardConfig), Regress78493232Dump_WithPhi.CLASS_NAME);
    assertEquals(expected, javaResult.stdout);
    assertEquals(0, javaResult.exitCode);
    switch (ToolHelper.getDexVm().getVersion()) {
      case V6_0_1:
        assertEquals("Completed successfully after 1000 iterations\n", d8Result.stdout);
        assertEquals("Completed successfully after 1000 iterations\n", r8Result.stdout);
        assertEquals("Completed successfully after 1000 iterations\n", r8ShakenResult.stdout);
        assertEquals(0, d8Result.exitCode);
        assertEquals(0, r8Result.exitCode);
        assertEquals(0, r8ShakenResult.exitCode);
        break;
      case V5_1_1:
        assertEquals(expected, d8Result.stdout);
        assertEquals(expected, r8Result.stdout);
        assertEquals(expected, r8ShakenResult.stdout);
        assertEquals(0, d8Result.exitCode);
        assertEquals(0, r8Result.exitCode);
        assertEquals(0, r8ShakenResult.exitCode);
        break;
      default:
        assertNotEquals(-1, d8Result.stderr.indexOf("java.lang.VerifyError"));
        assertNotEquals(-1, r8Result.stderr.indexOf("java.lang.VerifyError"));
        assertNotEquals(-1, r8ShakenResult.stderr.indexOf("java.lang.VerifyError"));
        assertEquals(1, d8Result.exitCode);
        assertEquals(1, r8Result.exitCode);
        assertEquals(1, r8ShakenResult.exitCode);
        break;
    }
  }
}
