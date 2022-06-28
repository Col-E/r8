// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredLibraryDumpInputsTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public DesugaredLibraryDumpInputsTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testDumpToDirectory() throws Exception {
    Assume.assumeTrue(libraryDesugaringSpecification.hasAnyDesugaring(parameters));
    Path dumpDir = temp.newFolder().toPath();
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> options.setDumpInputFlags(DumpInputFlags.dumpToDirectory(dumpDir)))
        .allowDiagnosticInfoMessages()
        .compile()
        .inspectDiagnosticMessages(
            diagnosticMessages ->
                diagnosticMessages.assertAllInfosMatch(
                    DiagnosticsMatcher.diagnosticMessage(
                        containsString("Dumped compilation inputs to:"))))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("PT42S");
    verifyDumpDir(dumpDir);
  }

  private void verifyDumpDir(Path dumpDir) throws IOException {
    assertTrue(Files.isDirectory(dumpDir));
    List<Path> paths = Files.walk(dumpDir, 1).collect(Collectors.toList());
    assertEquals(2, paths.size());
    boolean hasVerified = false;
    for (Path path : paths) {
      if (!path.equals(dumpDir)) {
        // The non-external run here results in assert code calling application read.
        verifyDump(path);
        hasVerified = true;
      }
    }
    assertTrue(hasVerified);
  }

  private void verifyDump(Path dumpFile) throws IOException {
    assertTrue(Files.exists(dumpFile));
    Path unzipped = temp.newFolder().toPath();
    ZipUtils.unzip(dumpFile.toString(), unzipped.toFile());
    assertTrue(Files.exists(unzipped.resolve("r8-version")));
    assertTrue(Files.exists(unzipped.resolve("program.jar")));
    assertTrue(Files.exists(unzipped.resolve("library.jar")));
    assertTrue(Files.exists(unzipped.resolve("desugared-library.json")));
    Path buildPropertiesPath = unzipped.resolve("build.properties");
    assertTrue(Files.exists(buildPropertiesPath));
    List<String> buildProperties = Files.readAllLines(buildPropertiesPath);
    assertTrue(buildProperties.get(0).startsWith("tool="));
    boolean isD8 = buildProperties.get(0).equals("tool=D8");
    boolean isR8 = buildProperties.get(0).equals("tool=R8");
    if ((compilationSpecification.isL8Shrink() || isR8) && !isD8) {
      assertTrue(Files.exists(unzipped.resolve("proguard.config")));
    } else {
      assertFalse(Files.exists(unzipped.resolve("proguard.config")));
    }
  }

  static class TestClass {
    public static void main(String[] args) {
      System.out.println(Duration.ofSeconds(42));
    }
  }
}
