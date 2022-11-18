// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.StringConsumer.FileConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.InternalOptions;
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
public class TraceReferenceDumpInputsTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withSystemRuntime().build();
  }

  public TraceReferenceDumpInputsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDumpToDirectory() throws Exception {
    Path dumpDir = temp.newFolder().toPath();
    TraceReferencesKeepRules keepRulesConsumer =
        TraceReferencesKeepRules.builder()
            .setAllowObfuscation(true)
            .setOutputConsumer(new FileConsumer(temp.newFile().toPath()))
            .build();
    TraceReferencesCommand command =
        TraceReferencesCommand.builder()
            .setConsumer(keepRulesConsumer)
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
            .addTargetFiles(ToolHelper.getClassFileForTestClass(TestClass.class))
            .addSourceFiles(ToolHelper.getClassFileForTestClass(OtherTestClass.class))
            .build();
    InternalOptions internalOptions = command.getInternalOptions();
    internalOptions.setDumpInputFlags(DumpInputFlags.dumpToDirectory(dumpDir));
    TraceReferences.runForTesting(command, internalOptions);
    verifyDumpDirectory(dumpDir);
  }

  private void verifyDumpDirectory(Path dumpDir) throws IOException {
    assertTrue(Files.isDirectory(dumpDir));
    List<Path> paths = Files.walk(dumpDir, 1).collect(Collectors.toList());
    boolean hasVerified = false;
    for (Path path : paths) {
      if (!path.equals(dumpDir)) {
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
    assertTrue(Files.exists(unzipped.resolve("build.properties")));
    assertTrue(Files.exists(unzipped.resolve("program.jar")));
    assertTrue(Files.exists(unzipped.resolve("library.jar")));
    assertTrue(Files.exists(unzipped.resolve("classpath.jar")));
    contains(unzipped, "program.jar", OtherTestClass.class);
    contains(unzipped, "classpath.jar", TestClass.class);
    checkProperties(unzipped.resolve("build.properties"));
  }

  private void checkProperties(Path properties) throws IOException {
    List<String> lines = Files.readAllLines(properties);
    assertEquals(4, lines.size());
    assertEquals("tool=TraceReferences", lines.get(0));
    assertEquals(
        "trace_references_consumer=com.android.tools.r8.tracereferences.TraceReferencesKeepRules",
        lines.get(2));
    assertEquals("minification=true", lines.get(3));
  }

  private void contains(Path unzipped, String jar, Class<?> clazz) throws IOException {
    Set<String> entries = new HashSet<>();
    ZipUtils.iter(unzipped.resolve(jar).toString(), (entry, input) -> entries.add(entry.getName()));
    assertTrue(
        entries.contains(
            DescriptorUtils.getClassFileName(
                DescriptorUtils.javaTypeToDescriptor(clazz.getTypeName()))));
  }

  static class TestClass {
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }

  static class OtherTestClass {
    public static void main(String[] args) {
      TestClass.main(args);
    }
  }
}
