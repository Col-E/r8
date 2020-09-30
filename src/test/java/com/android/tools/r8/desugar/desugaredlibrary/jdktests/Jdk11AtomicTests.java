// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.ToolHelper.JDK_TESTS_BUILD_DIR;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;
import static org.hamcrest.CoreMatchers.endsWith;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Jdk11AtomicTests extends Jdk11DesugaredLibraryTestBase {

  private static final Path ATOMIC_TESTS_FOLDER =
      Paths.get(ToolHelper.JDK_11_TESTS_DIR + "java/util/concurrent/atomic/");
  private static Path ATOMIC_COMPILED_TESTS_FOLDER;
  private static final String ATOMIC_REFERENCE_TEST = "AtomicReferenceTest";
  private static final String ATOMIC_UPDATERS = "AtomicUpdaters";
  private static final Path[] ATOMIC_TESTS_FILES =
      new Path[] {
        ATOMIC_TESTS_FOLDER.resolve(ATOMIC_REFERENCE_TEST + JAVA_EXTENSION),
        ATOMIC_TESTS_FOLDER.resolve(ATOMIC_UPDATERS + JAVA_EXTENSION)
      };

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    // TODO(b/134732760): Skip Android 4.4.4 due to missing libjavacrypto.
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters()
            .withDexRuntime(Version.V4_0_4)
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build());
  }

  public Jdk11AtomicTests(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @BeforeClass
  public static void compileAtomicClasses() throws Exception {
    ATOMIC_COMPILED_TESTS_FOLDER = getStaticTemp().newFolder("atomic").toPath();
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addClasspathFiles(
            Collections.singletonList(Paths.get(JDK_TESTS_BUILD_DIR + "testng-6.10.jar")))
        .addSourceFiles(ATOMIC_TESTS_FILES)
        .setOutputPath(ATOMIC_COMPILED_TESTS_FOLDER)
        .compile();
  }

  @Test
  public void testD8AtomicReference() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    String verbosity = "2";
    testForD8()
        .addProgramFiles(
            ATOMIC_COMPILED_TESTS_FOLDER.resolve(ATOMIC_REFERENCE_TEST + CLASS_EXTENSION))
        .addProgramFiles(testNGSupportProgramFiles())
        .addProgramFiles(getPathsFiles())
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .withArt6Plus64BitsLib()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), "TestNGMainRunner", verbosity, ATOMIC_REFERENCE_TEST)
        .assertSuccessWithOutputThatMatches(
            endsWith(StringUtils.lines(ATOMIC_REFERENCE_TEST + ": SUCCESS")));
  }

  @Test
  public void testD8AtomicUpdaters() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    String verbosity = "2";
    testForD8()
        .addProgramFiles(
            getAllFilesWithSuffixInDirectory(ATOMIC_COMPILED_TESTS_FOLDER, CLASS_EXTENSION))
        .addProgramFiles(testNGSupportProgramFiles())
        .addProgramFiles(getPathsFiles())
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .withArt6Plus64BitsLib()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), "TestNGMainRunner", verbosity, ATOMIC_UPDATERS)
        .assertSuccessWithOutputThatMatches(
            endsWith(StringUtils.lines(ATOMIC_UPDATERS + ": SUCCESS")));
  }
}
