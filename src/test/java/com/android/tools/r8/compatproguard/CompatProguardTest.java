// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.compatproguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.compatproguard.CompatProguard.CompatProguardOptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CompatProguardTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public CompatProguardTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private static CompatProguardOptions parseArgs(String... args) {
    return CompatProguard.CompatProguardOptions.parse(args);
  }

  @Test
  public void testDefaultDataResources() {
    CompatProguardOptions options = parseArgs();
    assertNull(options.output);
    assertEquals(1, options.minApi);
    assertFalse(options.forceProguardCompatibility);
    assertTrue(options.includeDataResources);
    assertFalse(options.multiDex);
    assertNull(options.mainDexList);
    assertEquals(0, options.proguardConfig.size());
  }

  @Test
  public void testShortLine() {
    CompatProguardOptions options = parseArgs("-");
    assertEquals(1, options.proguardConfig.size());
  }

  @Test
  public void testProguardOptions() {
    CompatProguardOptions options;

    options = parseArgs("-xxx");
    assertEquals(1, options.proguardConfig.size());
    options = parseArgs("-xxx", "xxx");
    assertEquals(1, options.proguardConfig.size());
    options = parseArgs("-xxx",  "-yyy");
    assertEquals(2, options.proguardConfig.size());
    options = parseArgs("-xxx", "xxx", "-yyy", "yyy");
    assertEquals(2, options.proguardConfig.size());
  }

  @Test
  public void testInjarsAndOutput() {
    CompatProguardOptions options;
    String injars = "input.jar";
    String output = "outputdir";
    options = parseArgs("-injars", injars, "--output", output);
    assertEquals(output, options.output);
    assertEquals(1, options.proguardConfig.size());
    options = parseArgs("--output", output, "-injars", injars);
    assertEquals(1, options.proguardConfig.size());
  }

  @Test
  public void testMainDexList() {
    CompatProguardOptions options;
    String mainDexList = "maindexlist.txt";

    options = parseArgs("--main-dex-list", mainDexList);
    assertEquals(mainDexList, options.mainDexList);
    options = parseArgs("--main-dex-list=" + mainDexList);
    assertEquals(mainDexList, options.mainDexList);
    options = parseArgs("--main-dex-list", mainDexList, "--minimal-main-dex");
    assertEquals(mainDexList, options.mainDexList);
    options = parseArgs("--minimal-main-dex", "--main-dex-list=" + mainDexList);
    assertEquals(mainDexList, options.mainDexList);
  }

  @Test
  public void testInclude() {
    CompatProguardOptions options = parseArgs("-include --my-include-file.txt");
    assertEquals(1, options.proguardConfig.size());
    assertEquals("-include --my-include-file.txt", options.proguardConfig.get(0));
  }

  @Test
  public void testNoLocalsOption() {
    CompatProguardOptions options = parseArgs("--no-locals");
    assertEquals(0, options.proguardConfig.size());
  }

  @Test
  public void testNoDataResources() {
    CompatProguardOptions options = parseArgs("--no-data-resources");
    assertFalse(options.includeDataResources);
  }
}
