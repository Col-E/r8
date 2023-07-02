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
import com.android.tools.r8.desugar.desugaredlibrary.test.DesugaredLibraryTestCompileResult;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Jdk11ConcurrentMapTests extends DesugaredLibraryTestBase {

  private static final Path CONCURRENT_TESTS_FOLDER =
      Paths.get(ToolHelper.JDK_11_TESTS_DIR + "java/util/concurrent/ConcurrentMap/");
  private static final Path CONCURRENT_HASH_TESTS_FOLDER =
      Paths.get(ToolHelper.JDK_11_TESTS_DIR + "java/util/concurrent/ConcurrentHashMap/");
  private static Path[] CONCURRENT_COMPILED_TESTS_FILES;
  private static Path[] CONCURRENT_HASH_COMPILED_TESTS_FILES;
  private static final Path[] SUPPORT_LIBS =
      new Path[]{
          Paths.get(ToolHelper.JDK_11_TESTS_DIR + "lib/testlibrary/jdk/testlibrary/Utils.java"),
          Paths.get(ToolHelper.JDK_11_TESTS_DIR + "lib/testlibrary/jdk/testlibrary/Asserts.java")
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

  public Jdk11ConcurrentMapTests(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @BeforeClass
  public static void compileConcurrentClasses() throws Exception {
    Path concurrentCompiledTestsFolder = getStaticTemp().newFolder("concurrentmap").toPath();
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addClasspathFiles(testNGPath())
        .addSourceFiles(getAllFilesWithSuffixInDirectory(CONCURRENT_TESTS_FOLDER, JAVA_EXTENSION))
        .setOutputPath(concurrentCompiledTestsFolder)
        .compile();
    CONCURRENT_COMPILED_TESTS_FILES =
        getAllFilesWithSuffixInDirectory(concurrentCompiledTestsFolder, CLASS_EXTENSION);
    assert CONCURRENT_COMPILED_TESTS_FILES.length > 0;

    List<Path> concurrentHashFilesAndDependencies = new ArrayList<>();
    Collections.addAll(
        concurrentHashFilesAndDependencies,
        getAllFilesWithSuffixInDirectory(CONCURRENT_HASH_TESTS_FOLDER, JAVA_EXTENSION));
    Collections.addAll(concurrentHashFilesAndDependencies, SUPPORT_LIBS);
    Path[] classesToCompile = concurrentHashFilesAndDependencies.toArray(new Path[0]);
    Path concurrentHashCompiledTestsFolder =
        getStaticTemp().newFolder("concurrenthashmap").toPath();
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addClasspathFiles(testNGPath())
        .addSourceFiles(classesToCompile)
        .setOutputPath(concurrentHashCompiledTestsFolder)
        .compile();
    CONCURRENT_HASH_COMPILED_TESTS_FILES =
        getAllFilesWithSuffixInDirectory(concurrentHashCompiledTestsFolder, CLASS_EXTENSION);
    assert CONCURRENT_HASH_COMPILED_TESTS_FILES.length > 0;
  }

  @Test
  public void testConcurrent() throws Exception {
    // TODO(b/134732760): Support Java 9+ libraries.
    // We skip the ConcurrentRemoveIf test because of the  non desugared class CompletableFuture.
    String verbosity = "2";
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramFiles(CONCURRENT_COMPILED_TESTS_FILES)
        .addProgramFiles(testNGSupportProgramFiles())
        .addProgramClassFileData(getTestNGMainRunner())
        .applyIf(
            !libraryDesugaringSpecification.hasNioFileDesugaring(parameters),
            b -> b.addProgramFiles(getPathsFiles()))
        .compile()
        .withArt6Plus64BitsLib()
        .run(parameters.getRuntime(), "TestNGMainRunner", verbosity, "ConcurrentModification")
        .assertSuccessWithOutputThatMatches(
            endsWith(StringUtils.lines("ConcurrentModification: SUCCESS")));
  }

  private Path[] concurrentHashTestToCompile() {
    // We exclude WhiteBox.class because of Method handles, they are not supported on old devices
    // and the test uses methods not present even on 28.
    List<Path> toCompile = new ArrayList<>();
    Collections.addAll(toCompile, CONCURRENT_HASH_COMPILED_TESTS_FILES);
    toCompile.removeIf(file -> file.getFileName().toString().equals("WhiteBox.class"));
    return toCompile.toArray(new Path[0]);
  }

  private String[] concurrentHashTestNGTestsToRun() {
    List<String> toRun = new ArrayList<>();
    // TODO(b/134732760): Support Java 9+ libraries.
    // We exclude ConcurrentAssociateTest and ConcurrentContainsKeyTest due to non
    // desugared class CompletableFuture.
    // toRun.add("ConcurrentAssociateTest");
    // toRun.add("ConcurrentContainsKeyTest");
    return toRun.toArray(new String[0]);
  }

  private String[] concurrentHashMainTestsToRun() {
    return new String[]{
        "MapCheck",
        // TODO(b/134732760): Support Java 9+ libraries.
        // Following fails due to non desugared class SplittableRandom.
        // "MapLoops",
        // TODO(b/134732760): Support Java 9+ libraries.
        // Following fails due to non desugared class CompletableFuture.
        // "ToArray",
        "DistinctEntrySetElements",
    };
  }

  @Test
  public void testD8ConcurrentHash() throws Exception {
    String verbosity = "2";
    DesugaredLibraryTestCompileResult<?> compileResult =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addProgramFiles(concurrentHashTestToCompile())
            .addProgramFiles(testNGSupportProgramFiles())
            .addProgramClassFileData(getTestNGMainRunner())
            .applyIf(
                !libraryDesugaringSpecification.hasNioFileDesugaring(parameters),
                b -> b.addProgramFiles(getPathsFiles()))
            .compile()
            .withArt6Plus64BitsLib();
    for (String className : concurrentHashTestNGTestsToRun()) {
      compileResult
          .run(parameters.getRuntime(), "TestNGMainRunner", verbosity, className)
          .assertSuccessWithOutputThatMatches(endsWith(StringUtils.lines(className + ": SUCCESS")));
    }
    for (String className : concurrentHashMainTestsToRun()) {
      // Main jdk tests relies on the main function running without issues.
      // Failure implies a runtime exception.
      // We ensure that everything could be resolved (no missing method or class)
      // with the assertion on stderr.
      compileResult.run(parameters.getRuntime(), className).assertSuccess();
    }
  }
}
