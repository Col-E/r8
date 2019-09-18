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
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.DesugaredLibraryConfiguration;
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
    DexItemFactory factory = new DexItemFactory();
    InternalOptions options1 = new InternalOptions(factory, new Reporter());
    options1.minApiLevel = parameters.getApiLevel().getLevel();
    options1.desugaredLibraryConfiguration =
        DesugaredLibraryConfigurationForTesting.configureLibraryDesugaringForProgramCompilation(
            parameters.getApiLevel().getLevel(), factory);

    Reporter reporter = new Reporter();
    InternalOptions options2 = new InternalOptions(factory, reporter);
    options2.minApiLevel = parameters.getApiLevel().getLevel();
    options2.desugaredLibraryConfiguration =
        new DesugaredLibraryConfigurationParser(
                factory, reporter, false, parameters.getApiLevel().getLevel())
            .parse(
                StringResource.fromFile(
                    Paths.get(
                        "src/test/java/com/android/tools/r8/desugar/corelib/desugar_jdk_libs.json")));

    assertConfigurationEquals(
        options1.desugaredLibraryConfiguration, options2.desugaredLibraryConfiguration);
  }

  @Test
  public void testCompatibilityLibrary() {
    DexItemFactory factory = new DexItemFactory();
    InternalOptions options1 = new InternalOptions(factory, new Reporter());
    options1.minApiLevel = parameters.getApiLevel().getLevel();
    options1.desugaredLibraryConfiguration =
        DesugaredLibraryConfigurationForTesting.configureLibraryDesugaringForLibraryCompilation(
            parameters.getApiLevel().getLevel(), factory);

    Reporter reporter = new Reporter();
    InternalOptions options2 = new InternalOptions(factory, reporter);
    options2.minApiLevel = parameters.getApiLevel().getLevel();
    options2.desugaredLibraryConfiguration =
        new DesugaredLibraryConfigurationParser(
                factory, reporter, true, parameters.getApiLevel().getLevel())
            .parse(
                StringResource.fromFile(
                    Paths.get(
                        "src/test/java/com/android/tools/r8/desugar/corelib/desugar_jdk_libs.json")));

    assertConfigurationEquals(
        options1.desugaredLibraryConfiguration, options2.desugaredLibraryConfiguration);
  }

  private void assertConfigurationEquals(
      DesugaredLibraryConfiguration libraryConfiguration1,
      DesugaredLibraryConfiguration libraryConfiguration2) {
    assertDictEquals(
        libraryConfiguration1.getRewritePrefix(), libraryConfiguration2.getRewritePrefix());
    assertDictEquals(
        libraryConfiguration1.getEmulateLibraryInterface(),
        libraryConfiguration2.getEmulateLibraryInterface());
    assertDictEquals(
        libraryConfiguration1.getBackportCoreLibraryMember(),
        libraryConfiguration2.getBackportCoreLibraryMember());
    assertRetargetEquals(
        libraryConfiguration1.getRetargetCoreLibMember(),
        libraryConfiguration2.getRetargetCoreLibMember());
    assertEquals(
        libraryConfiguration1.getDontRewriteInvocation().size(),
        libraryConfiguration1.getDontRewriteInvocation().size());
  }

  private void assertRetargetEquals(
      Map<DexString, Map<DexType, DexType>> retarget1,
      Map<DexString, Map<DexType, DexType>> retarget2) {
    assertEquals(retarget1.size(), retarget2.size());
    for (DexString dexString : retarget1.keySet()) {
      assert retarget2.containsKey(dexString);
      assertDictEquals(retarget1.get(dexString), retarget2.get(dexString));
    }
  }

  private <E> void assertDictEquals(Map<E, E> map1, Map<E, E> map2) {
    assertEquals(map1.size(), map2.size());
    for (E key : map1.keySet()) {
      assertTrue(map2.containsKey(key) && map1.get(key).equals(map2.get(key)));
    }
  }
}
