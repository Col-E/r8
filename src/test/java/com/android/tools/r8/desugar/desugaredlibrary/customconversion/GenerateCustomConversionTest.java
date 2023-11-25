// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.customconversion;

import static com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase.getAllFilesWithSuffixInDirectory;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.JavaCompilerTool;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.CustomConversionVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Assume;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenerateCustomConversionTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public GenerateCustomConversionTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testCustomConversionIsUpToDate() throws Exception {
    Assume.assumeFalse("JDK8 not present on windows", ToolHelper.isWindows());

    // Regenerate the files in a temp directory.
    Path customConversionDir = temp.newFolder("custom_conversion").toPath();
    Collection<Path> generatedJars = generateCustomConversions(temp, customConversionDir);

    for (CustomConversionVersion version : CustomConversionVersion.values()) {
      // Assert the file is generated.
      Path newFile = customConversionDir.resolve(version.getFileName());
      String message =
          "File " + newFile + " was not generated, but " + generatedJars + " were generated.";
      assertTrue(message, generatedJars.contains(newFile));
      assertTrue(message, Files.exists(newFile));

      // Assert the file matches the one in third_party.
      Path thirdPartyFile = ToolHelper.getDesugarLibConversions(version);
      uploadJarsToCloudStorageIfTestFails(
          (expected, actual) -> {
            verifySameFilesInJarInSameOrder(expected, actual);
            assertProgramsEqual(expected, actual);
            return filesAreEqual(expected, actual);
          },
          thirdPartyFile,
          newFile);
    }
  }

  private static void verifySameFilesInJarInSameOrder(Path expected, Path actual)
      throws IOException {
    try (ZipFile zipFile1 = new ZipFile(expected.toFile())) {
      try (ZipFile zipFile2 = new ZipFile(actual.toFile())) {
        Enumeration<? extends ZipEntry> entries1 = zipFile1.entries();
        List<String> s1 = new ArrayList<>();
        while (entries1.hasMoreElements()) {
          ZipEntry entry = entries1.nextElement();
          s1.add(entry.getName());
        }
        Enumeration<? extends ZipEntry> entries2 = zipFile2.entries();
        List<String> s2 = new ArrayList<>();
        while (entries2.hasMoreElements()) {
          ZipEntry entry = entries2.nextElement();
          s2.add(entry.getName());
        }
        assertEquals(s1, s2);
      }
    }
  }

  private static Path[] extractClasspath(Path customConversionDir) throws IOException {
    try (Stream<Path> files = Files.walk(customConversionDir)) {
      return files
          .filter(
              path ->
                  path.toString().endsWith(JAVA_EXTENSION) && !path.toString().startsWith("java"))
          .toArray(Path[]::new);
    }
  }

  private static Collection<Path> generateCustomConversions(
      TemporaryFolder temp, Path destinationDir) throws IOException {
    temp.create();
    Files.createDirectories(destinationDir);

    // Get the sources for the custom conversions.
    Path customConversionDir = Paths.get(ToolHelper.LIBRARY_DESUGAR_SOURCE_DIR, "java");
    Path[] sourceFiles =
        getAllFilesWithSuffixInDirectory(customConversionDir.resolve("java"), JAVA_EXTENSION);
    assert sourceFiles.length > 0;
    Path[] classpath = extractClasspath(customConversionDir);
    assert classpath.length > 0;

    // Generate the raw jar.
    Path compiledClasspath = destinationDir.resolve("classpath.jar");
    JavaCompilerTool.create(TestRuntime.getCheckedInJdk8(), temp)
        .addSourceFiles(classpath)
        .setOutputPath(compiledClasspath)
        .compile();
    Path raw = destinationDir.resolve("raw.jar");
    JavaCompilerTool.create(TestRuntime.getCheckedInJdk8(), temp)
        .addClasspathFiles(compiledClasspath)
        .addSourceFiles(sourceFiles)
        .setOutputPath(raw)
        .compile();
    assert verifyRawOutput(raw);

    // Asm rewrite the jar.
    Collection<Path> generateJars = GenerateCustomConversion.generateJars(raw, destinationDir);

    // No need to keep the raw jar.
    Files.delete(raw);

    return generateJars;
  }

  private static boolean verifyRawOutput(Path output) throws IOException {
    // The output should exist, with a non zero size, and there should be at least one class file.
    assert Files.exists(output);
    try (ZipFile zipFile = new ZipFile(output.toFile())) {
      assert zipFile.size() > 0;
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (entry.getName().endsWith(CLASS_EXTENSION)) {
          return true;
        }
      }
    }
    return false;
  }

  public static void main(String[] args) throws IOException {
    generateCustomConversions(
        ToolHelper.getTemporaryFolderForTest(), Paths.get(ToolHelper.CUSTOM_CONVERSION_DIR));
  }
}
