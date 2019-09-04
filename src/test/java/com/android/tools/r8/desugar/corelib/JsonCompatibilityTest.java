// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DesugaredLibraryConfigurationForTesting;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.desugar.DesugaredLibraryConfigurationParser;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class JsonCompatibilityTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public JsonCompatibilityTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testCompatibilityProgram() {
    InternalOptions options1 = new InternalOptions(new DexItemFactory(), new Reporter());
    options1.minApiLevel = parameters.getApiLevel().getLevel();
    options1.libraryConfiguration =
        DesugaredLibraryConfigurationForTesting.configureLibraryDesugaringForProgramCompilation(
            parameters.getApiLevel().getLevel());

    DexItemFactory factory = new DexItemFactory();
    Reporter reporter = new Reporter();
    InternalOptions options2 = new InternalOptions(factory, reporter);
    options2.minApiLevel = parameters.getApiLevel().getLevel();
    options2.libraryConfiguration =
        new DesugaredLibraryConfigurationParser(
                factory, reporter, false, parameters.getApiLevel().getLevel())
            .parse(
                StringResource.fromFile(
                    Paths.get(
                        "src/test/java/com/android/tools/r8/desugar/corelib/desugar_jdk_libs.json")));

    assertConfigurationEquals(options1.libraryConfiguration, options2.libraryConfiguration);
  }

  @Test
  public void testCompatibilityLibrary() {
    InternalOptions options1 = new InternalOptions(new DexItemFactory(), new Reporter());
    options1.minApiLevel = parameters.getApiLevel().getLevel();
    options1.libraryConfiguration =
        DesugaredLibraryConfigurationForTesting.configureLibraryDesugaringForLibraryCompilation(
            parameters.getApiLevel().getLevel());

    DexItemFactory factory = new DexItemFactory();
    Reporter reporter = new Reporter();
    InternalOptions options2 = new InternalOptions(factory, reporter);
    options2.minApiLevel = parameters.getApiLevel().getLevel();
    options2.libraryConfiguration =
        new DesugaredLibraryConfigurationParser(
                factory, reporter, true, parameters.getApiLevel().getLevel())
            .parse(
                StringResource.fromFile(
                    Paths.get(
                        "src/test/java/com/android/tools/r8/desugar/corelib/desugar_jdk_libs.json")));

    assertConfigurationEquals(options1.libraryConfiguration, options2.libraryConfiguration);
  }

  private void assertConfigurationEquals(
      com.android.tools.r8.ir.desugar.DesugaredLibraryConfiguration libraryConfiguration1,
      com.android.tools.r8.ir.desugar.DesugaredLibraryConfiguration libraryConfiguration2) {
    assertDictEquals(
        libraryConfiguration1.getRewritePrefix(), libraryConfiguration2.getRewritePrefix());
    assertDictEquals(
        libraryConfiguration1.getEmulateLibraryInterface(),
        libraryConfiguration2.getEmulateLibraryInterface());
    assertDictEquals(
        libraryConfiguration1.getBackportCoreLibraryMember(),
        libraryConfiguration2.getBackportCoreLibraryMember());
    assertDictEquals(
        libraryConfiguration1.getRetargetCoreLibMember(),
        libraryConfiguration2.getRetargetCoreLibMember());
    assertEquals(
        libraryConfiguration1.getDontRewriteInvocation().size(),
        libraryConfiguration1.getDontRewriteInvocation().size());
  }

  private void assertDictEquals(Map<String, String> map1, Map<String, String> map2) {
    assertEquals(map1.size(), map2.size());
    for (String key : map1.keySet()) {
      assertTrue(map2.containsKey(key) && map1.get(key).equals(map2.get(key)));
    }
  }
}
