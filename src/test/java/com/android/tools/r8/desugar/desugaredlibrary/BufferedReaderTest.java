// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ir.desugar.DesugaredLibraryConfiguration;
import com.android.tools.r8.ir.desugar.DesugaredLibraryConfigurationParser;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BufferedReaderTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters()
            .withAllRuntimes()
            .withAllApiLevelsAlsoForCf()
            .withApiLevel(AndroidApiLevel.N)
            .build());
  }

  public BufferedReaderTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  private String expectedOutput() {
    return StringUtils.lines(
        "Hello",
        "Larry",
        "Page",
        parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N)
            ? "Caught java.io.UncheckedIOException"
            : "Caught j$.io.UncheckedIOException");
  }

  DesugaredLibraryConfiguration configurationAlternative3(
      InternalOptions options, boolean libraryCompilation, TestParameters parameters) {
    // Parse the current configuration and amend the configuration for BufferedReader.lines. The
    // configuration is the same for both program and library.
    return new DesugaredLibraryConfigurationParser(
            options.dexItemFactory(),
            options.reporter,
            libraryCompilation,
            parameters.getApiLevel().getLevel())
        .parse(StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING_ALTERNATIVE_3));
  }

  private void configurationForProgramCompilation(InternalOptions options) {
    options.desugaredLibraryConfiguration = configurationAlternative3(options, false, parameters);
  }

  private void configurationForLibraryCompilation(InternalOptions options) {
    options.desugaredLibraryConfiguration = configurationAlternative3(options, true, parameters);
  }

  @Test
  public void testBufferedReaderD8Cf() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    // Use D8 to desugar with Java classfile output.
    Path jar =
        testForD8(Backend.CF)
            .addOptionsModification(this::configurationForProgramCompilation)
            .addInnerClasses(BufferedReaderTest.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .compile()
            // .inspect(this::checkRewrittenInvokes)
            .writeToZip();

    if (parameters.getRuntime().isDex()) {
      // Collection keep rules is only implemented in the DEX writer.
      String desugaredLibraryKeepRules = keepRuleConsumer.get();
      if (desugaredLibraryKeepRules != null) {
        assertEquals(0, desugaredLibraryKeepRules.length());
        desugaredLibraryKeepRules = "-keep class * { *; }";
      }

      // Convert to DEX without desugaring and run.
      testForD8()
          .addProgramFiles(jar)
          .setMinApi(parameters.getApiLevel())
          .disableDesugaring()
          .compile()
          .addDesugaredCoreLibraryRunClassPath(
              (apiLevel, keepRules, shrink) ->
                  buildDesugaredLibrary(
                      apiLevel,
                      keepRules,
                      shrink,
                      ImmutableList.of(),
                      this::configurationForLibraryCompilation),
              parameters.getApiLevel(),
              desugaredLibraryKeepRules,
              shrinkDesugaredLibrary)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(expectedOutput());
    } else {
      // Build the desugared library in class file format.
      Path desugaredLib =
          getDesugaredLibraryInCF(parameters, this::configurationForLibraryCompilation);

      // Run on the JVM with desuagred library on classpath.
      testForJvm()
          .addProgramFiles(jar)
          .addRunClasspathFiles(desugaredLib)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(expectedOutput());
    }
  }

  @Test
  public void testBufferedReaderD8() throws Exception {
    Assume.assumeTrue(parameters.getRuntime().isDex());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addOptionsModification(
            options ->
                options.desugaredLibraryConfiguration =
                    configurationAlternative3(options, false, parameters))
        .addInnerClasses(BufferedReaderTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            (apiLevel, keepRules, shrink) ->
                buildDesugaredLibrary(
                    apiLevel,
                    keepRules,
                    shrink,
                    ImmutableList.of(),
                    this::configurationForLibraryCompilation),
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput());
  }

  @Test
  public void testBufferedReaderR8() throws Exception {
    Assume.assumeTrue(parameters.getRuntime().isDex());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addOptionsModification(
            options ->
                options.desugaredLibraryConfiguration =
                    configurationAlternative3(options, false, parameters))
        .addInnerClasses(BufferedReaderTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .enableInliningAnnotations()
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            (apiLevel, keepRules, shrink) ->
                buildDesugaredLibrary(
                    apiLevel,
                    keepRules,
                    shrink,
                    ImmutableList.of(),
                    this::configurationForLibraryCompilation),
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput());
  }

  static class TestClass {

    @NeverInline
    public static void testBufferedReaderLines() throws Exception {
      try (BufferedReader reader = new BufferedReader(new StringReader("Hello\nLarry\nPage"))) {
        reader.lines().forEach(System.out::println);
      }
    }

    @NeverInline
    public static void testBufferedReaderLines_uncheckedIoException() throws Exception {
      BufferedReader reader = new BufferedReader(new StringReader(""));
      reader.close();
      try {
        reader.lines().count();
        System.out.println("UncheckedIOException expected");
      } catch (UncheckedIOException expected) {
        System.out.println("Caught " + expected.getClass().getName());
      } catch (Throwable t) {
        System.out.println("Caught unexpected" + t.getClass().getName());
      }
    }

    public static void main(String[] args) throws Exception {
      testBufferedReaderLines();
      testBufferedReaderLines_uncheckedIoException();
    }
  }
}
