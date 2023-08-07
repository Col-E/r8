// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.getTestNGMainRunner;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.testNGPath;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.testNGSupportProgramFiles;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11TestLibraryDesugaringSpecification.EXTENSION_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11TestLibraryDesugaringSpecification.JDK11_PATH_JAVA_BASE_EXT;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.DesugaredLibraryTestCompileResult;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Jdk11NioFileTests extends DesugaredLibraryTestBase {

  private static final Path JDK_11_NIO_TEST_FILES_DIR =
      Paths.get(ToolHelper.JDK_11_TESTS_DIR).resolve("java/nio/file");
  private static Path TEST_UTIL_JAR;
  private static List<byte[]> TEST_PROGRAM_CLASS_DATA;

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() throws Exception {
    List<LibraryDesugaringSpecification> specs;
    if (ToolHelper.isWindows()) {
      // The library configuration is not available on windows. Do not run anything.
      specs = ImmutableList.of();
    } else {
      Jdk11TestLibraryDesugaringSpecification.setUp();
      specs = ImmutableList.of(JDK11_PATH_JAVA_BASE_EXT);
    }
    return buildParameters(
        // TODO(134732760): Support Dalvik VMs, currently fails because libjavacrypto is required
        // and present only in ART runtimes.
        getTestParameters()
            .withDexRuntime(Version.V4_0_4)
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build(),
        specs,
        ImmutableList.of(D8_L8DEBUG, D8_L8SHRINK));
  }

  public Jdk11NioFileTests(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  private static final List<String> EXCLUDE_COMPILATION =
      ImmutableList.copyOf(
          new String[] {
            // We cannot run these tests due to missing dependencies.
            "java/nio/file/FileStore/Basic.java",
            "java/nio/file/Path/MacPathTest.java",
            "java/nio/file/WatchService/LotsOfEvents.java",
            "java/nio/file/Files/StreamLinesTest.java",
            "java/nio/file/Files/walkFileTree/FindTest.java",
            "java/nio/file/Files/DeleteOnClose.java",
            "java/nio/file/Files/CopyAndMove.java",
            "java/nio/file/FileSystem/Basic.java",
            // Skip module info not used on Android.
            "module-info.java"
          });

  private static final Set<String> EXPECTED_FAILING_CLASSES_DESUGARING_26 =
      ImmutableSet.of(
          // SecureDirectoryStream is not supported with desugared library, which leads to issues.
          "DirectoryStreamSecureDS",
          // Watch services are supported on high apis with desugared library, however, only
          // equality
          // and not identity is preserved which fails the tests.
          "WatchServiceBasic",
          "WatchServiceSensitivityModifier");

  // We distinguish 2 kinds of tests:
  // - Main tests, which are run by running the main method, and succeed if no error is raised.
  // - TestNG tests, which are run using testNG.
  private static final List<String> SUCCESSFUL_MAIN_TESTS =
      ImmutableList.of(
          "PathUriImportExport",
          "PathMisc",
          "probeContentTypeParallelProbes",
          "probeContentTypeForceLoad",
          "AclEntryEmptySet",
          "DirectoryStreamBasic",
          "DirectoryStreamSecureDS",
          "DirectoryStreamDriveLetter",
          "FileTimeBasic",
          "BasicFileAttributeViewCreationTime",
          "BasicFileAttributeViewBasic",
          "etcExceptions",
          "walkFileTreeTerminateWalk",
          "walkFileTreeNulls",
          "walkFileTreeCreateFileTree",
          "walkFileTreeMaxDepth",
          "walkFileTreeSkipSiblings",
          "walkFileTreeSkipSubtree",
          "FilesSBC",
          "FilesNameLimits",
          "FilesCustomOptions",
          "FilesLinks",
          "WatchServiceBasic",
          "WatchServiceFileTreeModifier",
          "WatchServiceDeleteInterference",
          "WatchServiceLotsOfCancels",
          "WatchServiceSensitivityModifier");
  private static final List<String> FAILING_MAIN_TESTS =
      ImmutableList.of(
          "PathPathOps",
          "PathMacPath",
          "DosFileAttributeViewBasic",
          "probeContentTypeBasic",
          "AclFileAttributeViewBasic",
          "UserDefinedFileAttributeViewBasic",
          "PosixFileAttributeViewBasic",
          "BasicFileAttributeViewUnixSocketFile",
          "p/pMain",
          "PathMatcherBasic",
          "walkFileTreeWalkWithSecurity",
          "FilesFileAttributes",
          "FilesInterruptCopy",
          "FilesTemporaryFiles",
          "FilesCheckPermissions",
          "FilesMisc",
          "WatchServiceMayFlies", // Works but longest to run by far.
          "WatchServiceWithSecurityManager",
          "WatchServiceUpdateInterference",
          "WatchServiceLotsOfCloses");
  private static final List<String> SUCCESSFUL_TESTNG_TESTS =
      ImmutableList.of("FilesStreamTest", "FilesBytesAndLines");
  private static final List<String> FAILING_TESTNG_TESTS =
      ImmutableList.of("spiSetDefaultProvider", "FilesReadWriteString");

  @BeforeClass
  public static void compileJdk11NioTests() throws Exception {
    Map<String, List<Path>> nioTestFileBuckets = getSourceFileBuckets();
    TEST_UTIL_JAR = jarTestUtils(nioTestFileBuckets.get("file"));
    nioTestFileBuckets.remove("file");
    TEST_PROGRAM_CLASS_DATA = new ArrayList<>();
    for (Entry<String, List<Path>> entry : nioTestFileBuckets.entrySet()) {
      Path[] compiledClasses = compile(entry.getKey(), entry.getValue(), TEST_UTIL_JAR);
      assert compiledClasses.length > 0;
      TEST_PROGRAM_CLASS_DATA.addAll(repackage(entry.getKey(), compiledClasses));
    }
    assert !TEST_PROGRAM_CLASS_DATA.isEmpty();
  }

  @NotNull
  private static Map<String, List<Path>> getSourceFileBuckets() throws IOException {
    Map<String, List<Path>> nioTestFileBuckets =
        Files.walk(JDK_11_NIO_TEST_FILES_DIR)
            .filter(path -> path.toString().endsWith(JAVA_EXTENSION))
            .filter(
                path ->
                    EXCLUDE_COMPILATION.stream().noneMatch(elem -> path.toString().endsWith(elem)))
            .collect(Collectors.groupingBy(item -> item.getParent().getFileName().toString()));
    assert nioTestFileBuckets.size() > 0;
    return nioTestFileBuckets;
  }

  private static Path jarTestUtils(List<Path> sourceFiles) throws IOException {
    Path[] fileCompiledClasses = compile("file", sourceFiles, null);
    String jarName = "testUtils.jar";
    Path output = fileCompiledClasses[0].getParent();
    List<String> cmdline = new ArrayList<>();
    cmdline.add(TestRuntime.getCheckedInJdk11().getJavaExecutable().getParent() + "/jar");
    cmdline.add("cf");
    cmdline.add(jarName);
    for (Path compile : fileCompiledClasses) {
      cmdline.add(output.relativize(compile).toString());
    }
    ProcessBuilder builder = new ProcessBuilder(cmdline);
    builder.directory(output.toFile());
    ProcessResult result = ToolHelper.runProcess(builder);
    assert result.exitCode == 0;
    return output.resolve(jarName);
  }

  private static List<byte[]> repackage(String prefix, Path[] compiledClasses) throws IOException {
    List<byte[]> data = new ArrayList<>();
    Map<String, String> rewrite = new HashMap<>();
    for (Path compiledClass : compiledClasses) {
      String fileName = compiledClass.getFileName().toString();
      String basicName = fileName.substring(0, fileName.length() - CLASS_EXTENSION.length());
      rewrite.put(basicName, prefix + basicName);
    }
    for (Path compiledClass : compiledClasses) {
      String fileName = compiledClass.getFileName().toString();
      String basicName = fileName.substring(0, fileName.length() - CLASS_EXTENSION.length());
      ClassFileTransformer classFileTransformer =
          transformer(compiledClass, Reference.classFromDescriptor("L" + basicName + ";"))
              .setClassDescriptor("L" + rewrite.get(basicName) + ";")
              .setSuper(type -> rewrite.getOrDefault(type, type))
              .rewriteEnlosingAndNestAttributes(type -> rewrite.getOrDefault(type, type));
      rewrite.forEach(
          (key, val) -> {
            classFileTransformer.replaceClassDescriptorInMethodInstructions(
                "L" + key + ";", "L" + val + ";");
            classFileTransformer.replaceClassDescriptorInMembers("L" + key + ";", "L" + val + ";");
          });
      data.add(classFileTransformer.transform());
    }
    return data;
  }

  private static Path[] compile(String name, List<Path> sourceFiles, Path cp) throws IOException {
    List<String> options =
        Arrays.asList(
            "--add-reads",
            "java.base=ALL-UNNAMED",
            "--patch-module",
            "java.base=" + EXTENSION_PATH);
    Path tmpDirectory = getStaticTemp().newFolder(name).toPath();
    List<Path> classpath = new ArrayList<>();
    classpath.add(EXTENSION_PATH);
    classpath.add(testNGPath());
    if (cp != null) {
      classpath.add(cp);
    }
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addOptions(options)
        .addClasspathFiles(classpath)
        .addSourceFiles(sourceFiles)
        .setOutputPath(tmpDirectory)
        .compile();
    return getAllFilesWithSuffixInDirectory(tmpDirectory, CLASS_EXTENSION);
  }

  @Test
  public void testNioFileDesugaredLib() throws Exception {
    String verbosity = "2";
    Path relativeExecutionDirectory = createRelativeExecutionDirectory();
    DesugaredLibraryTestCompileResult<?> compileResult =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addProgramFiles(TEST_UTIL_JAR)
            .addProgramClassFileData(TEST_PROGRAM_CLASS_DATA)
            .addProgramFiles(testNGSupportProgramFiles())
            .addProgramClassFileData(getTestNGMainRunner())
            .compile()
            .withRelativeExecutionDirectory(relativeExecutionDirectory)
            .withArt6Plus64BitsLib();
    int success = 0;
    List<String> failingClasses = new ArrayList<>();
    for (String mainTestClass : SUCCESSFUL_MAIN_TESTS) {
      SingleTestRunResult<?> run = compileResult.run(parameters.getRuntime(), mainTestClass);
      if (run.getExitCode() != 0) {
        System.out.println("Main Fail " + mainTestClass);
        failingClasses.add(mainTestClass);
      } else {
        success++;
      }
    }
    for (String testNGTestClass : SUCCESSFUL_TESTNG_TESTS) {
      SingleTestRunResult<?> result =
          compileResult.run(
              parameters.getRuntime(), "TestNGMainRunner", verbosity, testNGTestClass);
      if (!result.getStdOut().contains(StringUtils.lines(testNGTestClass + ": SUCCESS"))) {
        System.out.println("TestNG Fail " + testNGTestClass);
        failingClasses.add(testNGTestClass);
      } else {
        success++;
      }
    }
    System.out.println("Successes:" + success + "; failures:" + failingClasses.size());
    if (!failingClasses.isEmpty()) {
      System.out.println("Failing classes: " + failingClasses);
    }
    if (parameters.getDexRuntimeVersion().isOlderThan(Version.V8_1_0)) {
      // TODO(b/234689867): DesugaredFileSystemProvider is exercised, fix or understand remaining
      // failures.
      int sevenOffset = parameters.getDexRuntimeVersion() == Version.V7_0_0 ? -1 : 0;
      int shrinkOffset = compilationSpecification.isL8Shrink() ? -1 : 0;
      assertTrue(success >= 20 + sevenOffset + shrinkOffset);
    } else if (parameters.getApiLevel().isLessThan(AndroidApiLevel.O)) {
      // Desugaring high api level.
      assertEquals(26, success);
      assertEquals(3, failingClasses.size());
      assertTrue(failingClasses.containsAll(EXPECTED_FAILING_CLASSES_DESUGARING_26));
    } else {
      // No desugaring or partial desugaring, high api level.
      assertEquals(29, success);
      assertEquals(0, failingClasses.size());
    }
    clearDirectory(relativeExecutionDirectory);
  }

  @Test
  public void testNioFileAndroid() throws Exception {
    Assume.assumeFalse(
        "The package java.nio was not present on older devices, all tests fail.",
        parameters.getDexRuntimeVersion().isOlderThan(Version.V8_1_0));
    String verbosity = "2";
    Path relativeExecutionDirectory = createRelativeExecutionDirectory();
    D8TestCompileResult compileResult =
        testForD8(parameters.getBackend())
            .addProgramFiles(TEST_UTIL_JAR)
            .addProgramClassFileData(TEST_PROGRAM_CLASS_DATA)
            .addProgramFiles(testNGSupportProgramFiles())
            .addProgramClassFileData(getTestNGMainRunner())
            .addLibraryFiles(libraryDesugaringSpecification.getLibraryFiles())
            .compile()
            .withRelativeExecutionDirectory(relativeExecutionDirectory)
            .withArt6Plus64BitsLib();
    for (String mainTestClass : SUCCESSFUL_MAIN_TESTS) {
      compileResult.run(parameters.getRuntime(), mainTestClass).assertSuccess();
    }
    for (String testNGTestClass : SUCCESSFUL_TESTNG_TESTS) {
      SingleTestRunResult<?> result =
          compileResult.run(
              parameters.getRuntime(), "TestNGMainRunner", verbosity, testNGTestClass);
      assertTrue(
          "Failure in " + testNGTestClass + "\n" + result,
          result.getStdOut().contains(StringUtils.lines(testNGTestClass + ": SUCCESS")));
    }
    clearDirectory(relativeExecutionDirectory);
  }

  private static void clearDirectory(Path executionDirectory) throws IOException {
    try (Stream<Path> pathStream = Files.walk(executionDirectory)) {
      pathStream
          .sorted(Comparator.reverseOrder())
          .map(Path::toFile)
          .forEach(f -> assertTrue(f.delete()));
    }
    assertFalse(Files.exists(executionDirectory));
  }

  private Path createRelativeExecutionDirectory() throws IOException {
    // We need to create a relative directory (in r8) so it can be used as execution directory.
    return Files.createDirectories(
        Paths.get(
            "jdknio_"
                + compilationSpecification.toString()
                + "_"
                + parameters.getDexRuntimeVersion().toString()
                + "_"
                + parameters.getApiLevel().toString()));
  }
}
