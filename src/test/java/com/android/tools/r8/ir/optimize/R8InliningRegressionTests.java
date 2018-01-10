// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

/**
 * This test verifies that semantic of class initialization is preserved when a static method
 * invocation is inlined.
 */
// TODO(shertz) add CF output
public class R8InliningRegressionTests extends TestBase {

  @Test
  public void testStaticInlining_b71524812() throws Exception {
    buildAndTest("staticinlining", "staticinlining.Main");
  }

  @Test
  @Ignore("b/71629503")
  public void testInterfaceInlining_b71629503() throws Exception {
    buildAndTest("inlining_with_proxy", "inlining_with_proxy.Main");
  }

  private void buildAndTest(String folder, String mainClass) throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());

    Path proguardRules = Paths.get(ToolHelper.EXAMPLES_DIR, folder, "keep-rules.txt");
    Path jarFile =
        Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, folder + FileUtils.JAR_EXTENSION);

    // Build with R8
    AndroidApp.Builder builder = AndroidApp.builder();
    builder.addProgramFiles(jarFile);
    AndroidApp app = compileWithR8(builder.build(), proguardRules);

    // Compare original and generated DEX files.
    String originalDexFile = Paths
        .get(ToolHelper.EXAMPLES_BUILD_DIR, folder, ToolHelper.DEFAULT_DEX_FILENAME).toString();
    Path generatedDexFile = temp.getRoot().toPath().resolve("classes.jar");
    app.writeToZip(generatedDexFile, OutputMode.DexIndexed);
    String artOutput = ToolHelper
        .checkArtOutputIdentical(originalDexFile, generatedDexFile.toString(), mainClass,
            ToolHelper.getDexVm());

    // Compare with Java.
    ToolHelper.ProcessResult javaResult = ToolHelper.runJava(jarFile, mainClass);
    if (javaResult.exitCode != 0) {
      System.out.println(javaResult.stdout);
      System.err.println(javaResult.stderr);
      fail("JVM failed for: " + mainClass);
    }
    assertEquals("JVM and ART output differ", javaResult.stdout, artOutput);
  }

}
