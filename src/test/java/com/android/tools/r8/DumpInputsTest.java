// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
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

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntime(CfVm.JDK9).build();
  }

  public DumpInputsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDumpToFile() throws Exception {
    Path dump = temp.newFolder().toPath().resolve("dump.zip");
    try {
      testForExternalR8(parameters.getBackend())
          .useExternalJDK(parameters.getRuntime().asCf().getVm())
          .addJvmFlag("-Dcom.android.tools.r8.dumpinputtofile=" + dump)
          .addProgramClasses(TestClass.class)
          .compile();
    } catch (AssertionError e) {
      verifyDump(dump, false, true);
      return;
    }
    fail("Expected external compilation to exit");
  }

  @Test
  public void testDumpToDirectory() throws Exception {
    Path dumpDir = temp.newFolder().toPath();
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        // Setting a directory will allow compilation to continue.
        // Ensure the compilation and run can actually succeed.
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(options -> options.dumpInputToDirectory = dumpDir.toString())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
    assertTrue(Files.isDirectory(dumpDir));
    List<Path> paths = Files.walk(dumpDir, 1).collect(Collectors.toList());
    boolean hasVerified = false;
    for (Path path : paths) {
      if (!path.equals(dumpDir)) {
        // The non-external run here results in assert code calling application read.
        verifyDump(path, false, false);
        hasVerified = true;
      }
    }
    assertTrue(hasVerified);
  }

  private void verifyDump(Path dumpFile, boolean hasClasspath, boolean hasProguardConfig)
      throws IOException {
    assertTrue(Files.exists(dumpFile));
    Path unzipped = temp.newFolder().toPath();
    ZipUtils.unzip(dumpFile.toString(), unzipped.toFile());
    assertTrue(Files.exists(unzipped.resolve("r8-version")));
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

  static class TestClass {
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }
}
