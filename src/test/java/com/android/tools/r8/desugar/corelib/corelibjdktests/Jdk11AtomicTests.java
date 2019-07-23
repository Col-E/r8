// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib.corelibjdktests;

import static com.android.tools.r8.ToolHelper.JDK_TESTS_BUILD_DIR;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;
import static org.hamcrest.CoreMatchers.endsWith;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Jdk11AtomicTests extends Jdk11CoreLibTestBase {

  private static final Path ATOMIC_TESTS_FOLDER =
      Paths.get(ToolHelper.JDK_11_TESTS_DIR + "java/util/concurrent/atomic/");
  private static final Path ATOMIC_COMPILED_TESTS_FOLDER =
      Paths.get(ToolHelper.JDK_11_TESTS_CLASSES_DIR + "Atomic/");
  private static final String ATOMIC_REFERENCE_TEST = "AtomicReferenceTest";
  private static final String ATOMIC_UPDATERS = "AtomicUpdaters";
  private static final Path[] ATOMIC_TESTS_FILES =
      new Path[] {
        ATOMIC_TESTS_FOLDER.resolve(ATOMIC_REFERENCE_TEST + JAVA_EXTENSION),
        ATOMIC_TESTS_FOLDER.resolve(ATOMIC_UPDATERS + JAVA_EXTENSION)
      };
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // TODO(b/134732760): Skip Android 4.4.4 due to missing libjavacrypto.
    return getTestParameters()
        .withDexRuntime(Version.V4_0_4)
        .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
        //TODO(b/134732760): Add support for R8 and JDK11.
        .withCfRuntime(CfVm.JDK11)
        .withAllApiLevels()
        .build();
  }

  public Jdk11AtomicTests(TestParameters parameters) {
    this.parameters = parameters;
  }

  @BeforeClass
  public static void compileAtomicClasses() throws Exception {
    ToolHelper.runJavac(
        CfVm.JDK11,
        Collections.singletonList(Paths.get(JDK_TESTS_BUILD_DIR + "testng-6.10.jar")),
        ATOMIC_COMPILED_TESTS_FOLDER,
        ATOMIC_TESTS_FILES);
  }

  @Test
  public void testD8AtomicReference() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    Assume.assumeTrue("TODO(134732760): Fix Android 7+.", requiresCoreLibDesugaring(parameters));
    String verbosity = "2";
    testForD8()
        .addProgramFiles(
            ATOMIC_COMPILED_TESTS_FOLDER.resolve(ATOMIC_REFERENCE_TEST + CLASS_EXTENSION))
        .addProgramFiles(testNGSupportProgramFiles())
        .addProgramFiles(getPathsFiles())
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .compile()
        .withArt6Plus64BitsLib()
        .addRunClasspathFiles(buildDesugaredLibraryWithJavaBaseExtension(parameters.getApiLevel()))
        .run(parameters.getRuntime(), "TestNGMainRunner", verbosity, ATOMIC_REFERENCE_TEST)
        .assertSuccessWithOutputThatMatches(
            endsWith(StringUtils.lines(ATOMIC_REFERENCE_TEST + ": SUCCESS")));
  }

  @Test
  public void testD8AtomicUpdaters() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    Assume.assumeTrue("TODO(134732760): Fix Android 7+.", requiresCoreLibDesugaring(parameters));
    String verbosity = "2";
    testForD8()
        .addProgramFiles(
            getAllFilesWithSuffixInDirectory(ATOMIC_COMPILED_TESTS_FOLDER, CLASS_EXTENSION))
        .addProgramFiles(testNGSupportProgramFiles())
        .addProgramFiles(getPathsFiles())
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .compile()
        .withArt6Plus64BitsLib()
        .addRunClasspathFiles(buildDesugaredLibraryWithJavaBaseExtension(parameters.getApiLevel()))
        .run(parameters.getRuntime(), "TestNGMainRunner", verbosity, ATOMIC_UPDATERS)
        .assertSuccessWithOutputThatMatches(
            endsWith(StringUtils.lines(ATOMIC_UPDATERS + ": SUCCESS")));
  }
}
