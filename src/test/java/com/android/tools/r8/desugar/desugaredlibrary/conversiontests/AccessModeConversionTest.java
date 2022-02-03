// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyTopLevelFlags;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.AccessMode;
import java.nio.file.Path;
import java.util.List;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AccessModeConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.O;
  private static final String EXPECTED_RESULT =
      StringUtils.lines("READ", "WRITE", "READ", "WRITE", "EXECUTE");

  private static Path CUSTOM_LIB;

  @Parameters(name = "{0}, shrinkDesugaredLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED), BooleanUtils.values());
  }

  public AccessModeConversionTest(TestParameters parameters, boolean shrinkDesugaredLibrary) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @BeforeClass
  public static void compileCustomLib() throws Exception {
    CUSTOM_LIB =
        testForD8(getStaticTemp())
            .addProgramClasses(CustomLibClass.class)
            .setMinApi(MIN_SUPPORTED)
            .compile()
            .writeToZip();
  }

  private void configureDesugaredLibrary(InternalOptions options, boolean l8Compilation) {
    LegacyRewritingFlags rewritingFlags =
        LegacyRewritingFlags.builder(options.itemFactory, options.reporter, Origin.unknown())
            .putRewritePrefix("java.nio.file.AccessMode", "j$.nio.file.AccessMode")
            .addWrapperConversion("java.nio.file.AccessMode")
            .build();
    LegacyDesugaredLibrarySpecification specification =
        new LegacyDesugaredLibrarySpecification(
            LegacyTopLevelFlags.testing(), rewritingFlags, l8Compilation);
    setDesugaredLibrarySpecificationForTesting(options, specification);
  }

  @Test
  public void testD8() throws Exception {
    Assume.assumeTrue(isJDK11DesugaredLibrary());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addLibraryFiles(getLibraryFile())
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(Executor.class)
        .addLibraryClasses(CustomLibClass.class)
        .addOptionsModification(opt -> this.configureDesugaredLibrary(opt, false))
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            (apiLevel, keepRules, shrink) ->
                this.buildDesugaredLibrary(
                    apiLevel,
                    keepRules,
                    shrink,
                    ImmutableList.of(),
                    opt -> this.configureDesugaredLibrary(opt, true)),
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue(isJDK11DesugaredLibrary());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addLibraryFiles(getLibraryFile())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Executor.class)
        .addProgramClasses(Executor.class)
        .addLibraryClasses(CustomLibClass.class)
        .addOptionsModification(opt -> this.configureDesugaredLibrary(opt, false))
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            (apiLevel, keepRules, shrink) ->
                this.buildDesugaredLibrary(
                    apiLevel,
                    keepRules,
                    shrink,
                    ImmutableList.of(),
                    opt -> this.configureDesugaredLibrary(opt, true)),
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  static class Executor {

    public static void main(String[] args) {
      System.out.println(CustomLibClass.get(AccessMode.READ));
      System.out.println(CustomLibClass.get(AccessMode.WRITE));
      System.out.println(
          CustomLibClass.get(
              new AccessMode[] {AccessMode.READ, AccessMode.WRITE, AccessMode.EXECUTE})[0]);
      System.out.println(
          CustomLibClass.get(
              new AccessMode[] {AccessMode.READ, AccessMode.WRITE, AccessMode.EXECUTE})[1]);
      System.out.println(
          CustomLibClass.get(
              new AccessMode[] {AccessMode.READ, AccessMode.WRITE, AccessMode.EXECUTE})[2]);
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    public static AccessMode get(AccessMode mode) {
      return mode;
    }

    public static AccessMode[] get(AccessMode[] modes) {
      return modes;
    }
  }
}
