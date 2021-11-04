// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// See b/204518518.
@RunWith(Parameterized.class)
public class ProgramInterfaceWithLibraryMethod extends DesugaredLibraryTestBase {

  @Parameter(0)
  public TestParameters parameters;

  private static final String EXPECTED_RESULT = StringUtils.lines("Hello, world!");
  private static Path CUSTOM_LIB_DEX;
  private static Path CUSTOM_LIB_CF;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @BeforeClass
  public static void compileCustomLib() throws Exception {
    CUSTOM_LIB_DEX = getStaticTemp().newFolder().toPath().resolve("customLibDex.jar");
    testForD8(getStaticTemp())
        .addProgramClasses(LibraryClass.class)
        .setMinApi(AndroidApiLevel.B)
        .compile()
        .writeToZip(CUSTOM_LIB_DEX);
    CUSTOM_LIB_CF = getStaticTemp().newFolder().toPath().resolve("customLibCf.jar");
    ZipBuilder.builder(CUSTOM_LIB_CF)
        .addBytes(
            DescriptorUtils.getPathFromJavaType(LibraryClass.class),
            Files.readAllBytes(ToolHelper.getClassFileForTestClass(LibraryClass.class)))
        .build();
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addLibraryClasses(LibraryClass.class)
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(Executor.class, ProgramInterface.class, ProgramClass.class)
        .enableCoreLibraryDesugaring(
            LibraryDesugaringTestConfiguration.forApiLevel(parameters.getApiLevel()))
        .compile()
        .addRunClasspathFiles(CUSTOM_LIB_DEX)
        .run(parameters.getRuntime(), Executor.class)
        .applyIf(
            parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT),
            r -> r.assertFailureWithErrorThatThrows(AbstractMethodError.class));
  }

  @Test
  public void testD8CfToCf() throws Exception {
    Path jar =
        testForD8(Backend.CF)
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addLibraryClasses(LibraryClass.class)
            .addProgramClasses(Executor.class, ProgramInterface.class, ProgramClass.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(
                LibraryDesugaringTestConfiguration.forApiLevel(parameters.getApiLevel()))
            .compile()
            .writeToZip();
    if (parameters.getRuntime().isDex()) {
      testForD8()
          .addProgramFiles(jar)
          .setMinApi(parameters.getApiLevel())
          .disableDesugaring()
          .compile()
          .addDesugaredCoreLibraryRunClassPath(
              this::buildDesugaredLibrary, parameters.getApiLevel())
          .addRunClasspathFiles(CUSTOM_LIB_DEX)
          .run(parameters.getRuntime(), Executor.class)
          .applyIf(
              parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N),
              r -> r.assertSuccessWithOutput(EXPECTED_RESULT),
              r -> r.assertFailureWithErrorThatThrows(AbstractMethodError.class));
    } else {
      testForJvm()
          .addProgramFiles(jar)
          .addRunClasspathFiles(getDesugaredLibraryInCF(parameters, options -> {}))
          .addRunClasspathFiles(CUSTOM_LIB_CF)
          .run(parameters.getRuntime(), Executor.class)
          .applyIf(
              parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N),
              r -> r.assertSuccessWithOutput(EXPECTED_RESULT),
              r -> r.assertFailureWithErrorThatThrows(AbstractMethodError.class));
    }
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addLibraryClasses(LibraryClass.class)
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(Executor.class, ProgramInterface.class, ProgramClass.class)
        .addKeepMainRule(Executor.class)
        .enableCoreLibraryDesugaring(
            LibraryDesugaringTestConfiguration.forApiLevel(parameters.getApiLevel()))
        .compile()
        .addRunClasspathFiles(parameters.isDexRuntime() ? CUSTOM_LIB_DEX : CUSTOM_LIB_CF)
        .run(parameters.getRuntime(), Executor.class)
        .applyIf(
            parameters.isDexRuntime()
                && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT),
            r -> r.assertFailureWithErrorThatThrows(NoSuchMethodError.class));
  }

  static class Executor {

    public static void main(String[] args) {
      invoke(new ProgramClass());
    }

    static void invoke(ProgramInterface i) {
      i.methodTakingConsumer(null);
    }
  }

  interface ProgramInterface {
    void methodTakingConsumer(Consumer<String> consumer);
  }

  static class ProgramClass extends LibraryClass implements ProgramInterface {
    // TODO(b/204518518): Adding this forwarding method fixes the issue.
    // public void methodTakingConsumer(Consumer<String> consumer) {
    //   super.methodTakingConsumer(consumer);
    // }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class LibraryClass {

    public void methodTakingConsumer(Consumer<String> consumer) {
      System.out.println("Hello, world!");
    }
  }
}
