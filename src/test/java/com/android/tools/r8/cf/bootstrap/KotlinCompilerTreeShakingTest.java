// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.bootstrap;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinCompilerTool.KotlinTargetVersion;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.internal.CompilationTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinCompilerTreeShakingTest extends CompilationTestBase {

  private static final String PKG_NAME = KotlinCompilerTreeShakingTest.class.getPackage().getName();
  private static final Path HELLO_KT =
      Paths.get(
          ToolHelper.TESTS_DIR,
          "java",
          DescriptorUtils.getBinaryNameFromJavaType(PKG_NAME),
          "Hello.kt");
  private static final int MAX_SIZE = (int) (31361268 * 0.4);

  private final TestParameters parameters;
  private final KotlinTestParameters kotlinTestParameters;

  @Parameters(name = "{0}, kotlinc: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public KotlinCompilerTreeShakingTest(
      TestParameters parameters, KotlinTestParameters kotlinTestParameters) {
    this.parameters = parameters;
    this.kotlinTestParameters = kotlinTestParameters;
  }

  @Test
  public void testForRuntime() throws Exception {
    // Compile Hello.kt and make sure it works as expected.
    Path classPathBefore =
        kotlinc(
                KotlinTestBase.getKotlincHostRuntime(parameters.getRuntime()),
                kotlinTestParameters.getCompiler(),
                kotlinTestParameters.getTargetVersion())
            .addSourceFiles(HELLO_KT)
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm(parameters)
        .addRunClasspathFiles(kotlinTestParameters.getCompiler().getKotlinStdlibJar())
        .addClasspath(classPathBefore)
        .run(parameters.getRuntime(), PKG_NAME + ".HelloKt")
        .assertSuccessWithOutputLines("I'm Woody. Howdy, howdy, howdy.");
  }

  @Ignore(
      "b/136457753: assertion error in static class merger; "
          + "b/144877828: assertion error in method naming state during interface method renaming; "
          + "b/144859533: umbrella"
  )

  @Test
  public void test() throws Exception {
    KotlinCompiler kotlinc = kotlinTestParameters.getCompiler();
    List<Path> libs =
        ImmutableList.of(
            kotlinc.getKotlinStdlibJar(),
            kotlinc.getKotlinReflectJar(),
            kotlinc.getKotlinScriptRuntime());
    // Process kotlin-compiler.jar.
    Path r8ProcessedKotlinc =
        testForR8(parameters.getBackend())
            .addLibraryFiles(libs)
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .addProgramFiles(kotlinc.getCompiler())
            .addKeepAttributes("*Annotation*")
            .addKeepClassAndMembersRules(ToolHelper.K2JVMCompiler)
            .addKeepClassAndMembersRules("**.K2JVMCompilerArguments")
            .addKeepClassAndMembersRules("**.*Argument*")
            .addKeepClassAndMembersRules("**.Freezable")
            .addKeepRules(
                "-keepclassmembers class * {", "  *** parseCommandLineArguments(...);", "}")
            .addKeepRules(
                "-keepclassmembers,allowoptimization enum * {",
                "    public static **[] values();",
                "    public static ** valueOf(java.lang.String);",
                "}")
            .addOptionsModification(
                o -> {
                  // Ignore com.sun.tools.javac.main.JavaCompiler and others
                  // Resulting jar may not be able to deal with .java source files, though.
                  o.ignoreMissingClasses = true;
                  // b/144861100: invoke-static on interface is allowed up to JDK 8.
                  o.testing.allowInvokeErrors = true;
                })
            .compile()
            .writeToZip();

    // Copy libraries used by kotlin-compiler.jar so that Preloader can load them.
    Path dir = r8ProcessedKotlinc.getParent();
    for (Path lib : libs) {
      Path newLib = dir.resolve(lib.getFileName());
      Files.copy(lib, newLib, REPLACE_EXISTING);
    }

    // TODO(b/144859533): passing `dir` as -kotlin-home.
    // Compile Hello.kt again with r8-processed kotlin-compiler.jar
    Path classPathAfter =
        kotlinc(
                parameters.getRuntime().asCf(),
                new KotlinCompiler(
                    "r8ProcessedKotlinc", r8ProcessedKotlinc, kotlinc.getCompilerVersion()),
                KotlinTargetVersion.JAVA_8)
            .addSourceFiles(HELLO_KT)
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar())
        .addClasspath(classPathAfter)
        .run(parameters.getRuntime(), PKG_NAME + ".HelloKt")
        .assertSuccessWithOutputLines("I'm Woody. Howdy, howdy, howdy.");

    int size = AndroidApp.builder().addProgramFile(r8ProcessedKotlinc).build().applicationSize();
    assertTrue(size <= MAX_SIZE);
  }
}
