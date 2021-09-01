// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.ZipUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceApiBinaryCompatibilityTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetraceApiBinaryCompatibilityTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static final Path BINARY_COMPATIBILITY_JAR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "retrace", "binary_compatibility", "tests.jar");

  private Path generateJar() throws Exception {
    return RetraceApiTestHelper.generateJarForRetraceBinaryTests(
        temp, RetraceApiTestHelper.getBinaryCompatibilityTests());
  }

  @Test
  public void testBinaryJarIsUpToDate() throws Exception {
    Path binaryContents = temp.newFolder().toPath();
    Path generatedContents = temp.newFolder().toPath();
    ZipUtils.unzip(BINARY_COMPATIBILITY_JAR, binaryContents);
    ZipUtils.unzip(generateJar(), generatedContents);
    try (Stream<Path> existingPaths = Files.walk(binaryContents);
        Stream<Path> generatedPaths = Files.walk(generatedContents)) {
      List<Path> existing = existingPaths.filter(this::isClassFile).collect(Collectors.toList());
      List<Path> generated = generatedPaths.filter(this::isClassFile).collect(Collectors.toList());
      assertEquals(existing.size(), generated.size());
      assertNotEquals(0, existing.size());
      for (Path classFile : generated) {
        Path otherClassFile = binaryContents.resolve(generatedContents.relativize(classFile));
        assertTrue(Files.exists(otherClassFile));
        assertTrue(TestBase.filesAreEqual(classFile, otherClassFile));
      }
    }
  }

  private boolean isClassFile(Path path) {
    return path.toString().endsWith(".class");
  }

  @Test
  public void runCheckedInBinaryJar() throws Exception {
    for (CfRuntime cfRuntime : CfRuntime.getCheckedInCfRuntimes()) {
      RetraceApiTestHelper.runJunitOnTests(
          cfRuntime,
          ToolHelper.R8_RETRACE_JAR,
          BINARY_COMPATIBILITY_JAR,
          RetraceApiTestHelper.getBinaryCompatibilityTests());
    }
  }

  public static void main(String[] args) throws Exception {
    TemporaryFolder temp = new TemporaryFolder();
    temp.create();
    Path generatedJar =
        RetraceApiTestHelper.generateJarForRetraceBinaryTests(
            temp, RetraceApiTestHelper.getBinaryCompatibilityTests());
    Files.move(generatedJar, BINARY_COMPATIBILITY_JAR, REPLACE_EXISTING);
  }
}
