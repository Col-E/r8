// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Collection;
import java.util.function.Consumer;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ProcessKotlinStdlibTest extends KotlinTestBase {
  private final Backend backend;

  public ProcessKotlinStdlibTest(Backend backend, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.backend = backend;
  }

  @Parameterized.Parameters(name = "Backend: {0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(ToolHelper.getBackends(), KotlinTargetVersion.values());
  }

  private void test(String... rules) throws Exception {
    testForR8(backend)
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addKeepRules(rules)
        .compile();
  }

  private void test(Consumer<InternalOptions> optionsConsumer, String... rules) throws Exception {
    testForR8(backend)
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addOptionsModification(optionsConsumer)
        .addKeepRules(rules)
        .compile();
  }

  @Test
  public void testAsIs() throws Exception {
    test("-dontshrink", "-dontoptimize", "-dontobfuscate");
  }

  @Test
  public void testDontShrinkAndDontOptimize() throws Exception {
    test("-dontshrink", "-dontoptimize");
  }

  @Ignore("b/129558497")
  @Test
  public void testDontShrinkAndDontOptimizeDifferently() throws Exception {
     test(
        o -> {
          o.enableTreeShaking = false;
          o.enableVerticalClassMerging = false;
        },
        "-keep,allowobfuscation class **.*Exception*");
  }

  @Test
  public void testDontShrinkAndDontObfuscate() throws Exception {
    test("-dontshrink", "-dontobfuscate");
  }

  @Test
  public void testDontShrink() throws Exception {
    test("-dontshrink");
  }

  @Ignore("b/129557890")
  @Test
  public void testDontShrinkDifferently() throws Exception {
    test(
        o -> o.enableTreeShaking = false,
        "-keep,allowobfuscation class **.*Exception*");
  }

  @Test
  public void testDontOptimize() throws Exception {
    test("-dontoptimize");
  }

  @Test
  public void testDontObfuscate() throws Exception {
    test("-dontobfuscate");
  }
}
