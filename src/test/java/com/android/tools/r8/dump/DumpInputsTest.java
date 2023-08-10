// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dump;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DumpInputsTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withSystemRuntime().build();
  }

  public DumpInputsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDumpToFileOptionsModification() throws Exception {
    Path dump = temp.newFolder().toPath().resolve("dump.zip");
    try {
      testForR8(parameters.getBackend())
          .addProgramClasses(TestClass.class)
          .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
          .addKeepMainRule(TestClass.class)
          .addOptionsModification(
              options -> options.setDumpInputFlags(DumpInputFlags.dumpToFile(dump)))
          .allowDiagnosticErrorMessages()
          .compileWithExpectedDiagnostics(
              diagnostics ->
                  diagnostics.assertErrorsMatch(
                      diagnosticMessage(containsString("Dumped compilation inputs to:"))));
      fail("Expected compilation to fail");
    } catch (CompilationFailedException e) {
      // Expected.
    }
    verifyDump(dump, false, true);
  }

  @Test
  public void testDumpToFileSystemProperty() throws Exception {
    Path dump = temp.newFolder().toPath().resolve("dump.zip");
    try {
      testForExternalR8(parameters.getBackend(), parameters.getRuntime())
          .addJvmFlag("-Dcom.android.tools.r8.dumpinputtofile=" + dump)
          .addProgramClasses(TestClass.class)
          .compile();
      fail("Expected external compilation to exit");
    } catch (AssertionError e) {
      // Expected.
    }
    verifyDump(dump, false, true);
  }

  @Test
  public void testDumpToFileSystemPropertyWhenMinifying() throws Exception {
    for (boolean minification : BooleanUtils.values()) {
      Path dump = temp.newFolder().toPath().resolve("dump.zip");
      try {
        testForExternalR8(parameters.getBackend(), parameters.getRuntime())
            .addJvmFlag("-Dcom.android.tools.r8.dumpinputtofile=" + dump)
            .addJvmFlag("-Dcom.android.tools.r8.dump.filter.buildproperty.minification=^true$")
            .addProgramClasses(TestClass.class)
            .applyIf(!minification, builder -> builder.addKeepRules("-dontobfuscate"))
            .compile();
        // Without minification there should be no dump, and thus compilation should succeed.
        assertFalse(minification);
      } catch (AssertionError e) {
        assertTrue(minification);
      }
      if (minification) {
        verifyDump(dump, false, true);
      } else {
        assertFalse(Files.exists(dump));
      }
    }
  }

  @Test
  public void testDumpToFileCLI() throws Exception {
    Path dump = temp.newFolder().toPath().resolve("dump.zip");
    try {
      testForExternalR8(parameters.getBackend(), parameters.getRuntime())
          .dumpInputToFile(dump.toString())
          .addProgramClasses(TestClass.class)
          .compile();
    } catch (AssertionError e) {
      verifyDump(dump, false, true);
      return;
    }
    fail("Expected external compilation to exit");
  }

  @Test
  public void testDumpToDirectoryOptionsModification() throws Exception {
    Path dumpDir = temp.newFolder().toPath();
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        // Setting a directory will allow compilation to continue.
        // Ensure the compilation and run can actually succeed.
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> options.setDumpInputFlags(DumpInputFlags.dumpToDirectory(dumpDir)))
        .allowDiagnosticInfoMessages()
        .compile()
        .assertAllInfoMessagesMatch(containsString("Dumped compilation inputs to:"))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
    verifyDumpDirectory(dumpDir, false, true);
  }

  @Test
  public void testDumpToDirectorySystemProperty() throws Exception {
    Path dumpDir = temp.newFolder().toPath();
    testForExternalR8(parameters.getBackend(), parameters.getRuntime())
        .addJvmFlag("-Dcom.android.tools.r8.dumpinputtodirectory=" + dumpDir)
        .addProgramClasses(TestClass.class)
        // Setting a directory will allow compilation to continue.
        // Ensure the compilation and run can actually succeed.
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .addKeepMainRule(TestClass.class)
        .compile();
    verifyDumpDirectory(dumpDir, false, true);
  }

  @Test
  public void testDumpToDirectoryCLI() throws Exception {
    Path dumpDir = temp.newFolder().toPath();
    testForExternalR8(parameters.getBackend(), parameters.getRuntime())
        .dumpInputToDirectory(dumpDir.toString())
        .addProgramClasses(TestClass.class)
        .compile();
    verifyDumpDirectory(dumpDir, false, false);
  }

  private void verifyDump(Path dumpFile, boolean hasClasspath, boolean hasProguardConfig)
      throws IOException {
    assertTrue(Files.exists(dumpFile));
    Path unzipped = temp.newFolder().toPath();
    ZipUtils.unzip(dumpFile.toString(), unzipped.toFile());
    assertTrue(Files.exists(unzipped.resolve("r8-version")));
    assertTrue(Files.exists(unzipped.resolve("build.properties")));
    assertTrue(Files.exists(unzipped.resolve("program.jar")));
    assertTrue(Files.exists(unzipped.resolve("library.jar")));
    if (hasClasspath) {
      assertTrue(Files.exists(unzipped.resolve("classpath.jar")));
    }
    if (hasProguardConfig) {
      assertTrue(Files.exists(unzipped.resolve("proguard.config")));
    }
    Set<String> entries = new HashSet<>();
    ZipUtils.iter(
        unzipped.resolve("program.jar").toString(), (entry, input) -> entries.add(entry.getName()));
    assertTrue(
        entries.contains(
            DescriptorUtils.getClassFileName(
                DescriptorUtils.javaTypeToDescriptor(TestClass.class.getTypeName()))));
  }

  private void verifyDumpDirectory(Path dumpDir, boolean hasClasspath, boolean hasProguardConfig)
      throws IOException {
    assertTrue(Files.isDirectory(dumpDir));
    List<Path> paths = Files.walk(dumpDir, 1).collect(Collectors.toList());
    boolean hasVerified = false;
    for (Path path : paths) {
      if (!path.equals(dumpDir)) {
        // The non-external run here results in assert code calling application read.
        verifyDump(path, hasClasspath, hasProguardConfig);
        hasVerified = true;
      }
    }
    assertTrue(hasVerified);
  }

  static class TestClass {
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }
}
