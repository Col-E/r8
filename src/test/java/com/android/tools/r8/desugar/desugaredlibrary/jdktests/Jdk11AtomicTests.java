// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.getPathsFiles;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.getTestNGMainRunner;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.testNGPath;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.testNGSupportProgramFiles;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;
import static org.hamcrest.CoreMatchers.endsWith;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Jdk11AtomicTests extends DesugaredLibraryTestBase {

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
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        // TODO(b/134732760): Skip Android 4.4.4 due to missing libjavacrypto.
        getTestParameters()
            .withDexRuntime(Version.V4_0_4)
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build(),
        ImmutableList.of(JDK8, JDK11, JDK11_PATH),
        ImmutableList.of(D8_L8DEBUG, D8_L8SHRINK));
  }

  public Jdk11AtomicTests(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @BeforeClass
  public static void compileAtomicClasses() throws Exception {
    ATOMIC_COMPILED_TESTS_FOLDER = getStaticTemp().newFolder("atomic").toPath();
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addClasspathFiles(testNGPath())
        .addSourceFiles(ATOMIC_TESTS_FILES)
        .setOutputPath(ATOMIC_COMPILED_TESTS_FOLDER)
        .compile();
  }

  @Test
  public void testAtomicReference() throws Exception {
    String verbosity = "2";
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramFiles(
            ATOMIC_COMPILED_TESTS_FOLDER.resolve(ATOMIC_REFERENCE_TEST + CLASS_EXTENSION))
        .addProgramFiles(testNGSupportProgramFiles())
        .addProgramClassFileData(getTestNGMainRunner())
        .applyIf(
            !libraryDesugaringSpecification.hasNioFileDesugaring(parameters),
            b -> b.addProgramFiles(getPathsFiles()))
        .compile()
        .withArt6Plus64BitsLib()
        .run(parameters.getRuntime(), "TestNGMainRunner", verbosity, ATOMIC_REFERENCE_TEST)
        .assertSuccessWithOutputThatMatches(
            endsWith(StringUtils.lines(ATOMIC_REFERENCE_TEST + ": SUCCESS")));
  }

  @Test
  public void testD8AtomicUpdaters() throws Exception {
    String verbosity = "2";
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramFiles(
            getAllFilesWithSuffixInDirectory(ATOMIC_COMPILED_TESTS_FOLDER, CLASS_EXTENSION))
        .addProgramFiles(testNGSupportProgramFiles())
        .addProgramClassFileData(getTestNGMainRunner())
        .applyIf(
            !libraryDesugaringSpecification.hasNioFileDesugaring(parameters),
            b -> b.addProgramFiles(getPathsFiles()))
        .compile()
        .withArt6Plus64BitsLib()
        .run(parameters.getRuntime(), "TestNGMainRunner", verbosity, ATOMIC_UPDATERS)
        .assertSuccessWithOutputThatMatches(
            endsWith(StringUtils.lines(ATOMIC_UPDATERS + ": SUCCESS")));
  }
}
