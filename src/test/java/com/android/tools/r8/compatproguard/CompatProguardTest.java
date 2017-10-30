// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.compatproguard;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.compatproguard.CompatProguard.CompatProguardOptions;
import org.junit.Test;

public class CompatProguardTest {

  private CompatProguardOptions parseArgs(String... args)throws Exception  {
    return CompatProguard.CompatProguardOptions.parse(args);
  }

  @Test
  public void testProguardOptions() throws Exception {
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
  public void testInjarsAndOutput() throws Exception {
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
  public void testMainDexList() throws Exception {
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
}
