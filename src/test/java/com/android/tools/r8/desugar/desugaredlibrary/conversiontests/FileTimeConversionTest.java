// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanTopLevelFlags;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FileTimeConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.O;
  private static final String EXPECTED_RESULT = StringUtils.lines("1970-01-01T00:00:01.234Z");

  private static Path CUSTOM_LIB;

  @Parameters(name = "{0}, shrinkDesugaredLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED), BooleanUtils.values());
  }

  public FileTimeConversionTest(TestParameters parameters, boolean shrinkDesugaredLibrary) {
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
    HumanRewritingFlags rewritingFlags =
        HumanRewritingFlags.builder(options.reporter, Origin.unknown())
            .putRewritePrefix("java.nio.file.attribute.FileTime", "j$.nio.file.attribute.FileTime")
            .putRewritePrefix(
                "java.nio.file.attribute.FileAttributeConversions",
                "j$.nio.file.attribute.FileAttributeConversions")
            .putRewriteDerivedPrefix(
                "java.nio.file.attribute.FileTime",
                "j$.nio.file.attribute.FileTime",
                "java.nio.file.attribute.FileTime")
            .putCustomConversion(
                options.dexItemFactory().createType("Ljava/nio/file/attribute/FileTime;"),
                options
                    .dexItemFactory()
                    .createType("Ljava/nio/file/attribute/FileAttributeConversions;"))
            .build();
    HumanDesugaredLibrarySpecification specification =
        new HumanDesugaredLibrarySpecification(
            HumanTopLevelFlags.testing(), rewritingFlags, l8Compilation);
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
        .addOptionsModification(opt -> opt.desugaredLibraryKeepRuleConsumer = keepRuleConsumer)
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
        .inspect(this::assertCallsToConversion)
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
        .addOptionsModification(opt -> opt.desugaredLibraryKeepRuleConsumer = keepRuleConsumer)
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

  private void assertCallsToConversion(CodeInspector codeInspector) {
    assertTrue(
        codeInspector
            .clazz(Executor.class)
            .uniqueMethodWithFinalName("main")
            .streamInstructions()
            .anyMatch(
                i ->
                    i.isInvokeStatic()
                        && i.getMethod()
                            .getHolderType()
                            .toString()
                            .contains("ExternalSyntheticAPIConversion")));
  }

  static class Executor {

    public static void main(String[] args) {
      System.out.println(CustomLibClass.get(FileTime.fromMillis(1234L)));
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    public static FileTime get(FileTime fileTime) {
      return fileTime;
    }
  }
}
