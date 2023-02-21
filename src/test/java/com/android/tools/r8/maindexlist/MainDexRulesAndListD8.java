// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.maindexlist;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MainDexRulesAndListD8 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withApiLevelsWithoutNativeMultiDex().build();
  }

  public MainDexRulesAndListD8(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static Path testDir;
  private static Path mainDexRules;
  private static Path mainDexList;

  @BeforeClass
  public static void setUp() throws Exception {
    testDir = getStaticTemp().newFolder().toPath();
    mainDexRules = testDir.resolve("main-dex-rules");
    mainDexList = testDir.resolve("main-dex-list");
    FileUtils.writeTextFile(mainDexRules, ImmutableList.of("-keep class " + A.class.getTypeName()));
    FileUtils.writeTextFile(
        mainDexList, ImmutableList.of(B.class.getTypeName().replace('.', '/') + ".class"));
  }

  @Test
  public void test() throws Exception {
    Path result =
        testForD8(parameters.getBackend())
            .setMinApi(parameters)
            .addInnerClasses(getClass())
            .addMainDexRulesFiles(mainDexRules)
            .addMainDexListFiles(mainDexList)
            .debug()
            .compile()
            .writeToZip();
    List<Path> dexFiles =
        ZipUtils.unzip(result, testDir).stream().sorted().collect(Collectors.toList());
    assertEquals(
        classNamesFromDexFile(dexFiles.get(0)).stream().sorted().collect(Collectors.toList()),
        ImmutableList.of(A.class.getTypeName(), B.class.getTypeName()));
    assertEquals(
        classNamesFromDexFile(dexFiles.get(1)).stream().sorted().collect(Collectors.toList()),
        ImmutableList.of(C.class.getTypeName()));
  }

  static class A {}

  static class B {}

  static class C {}
}
