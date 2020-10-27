// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
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

@RunWith(Parameterized.class)
public class DesugaredLibraryDumpInputsTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameterized.Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public DesugaredLibraryDumpInputsTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testD8DumpToDirectory() throws Exception {
    Assume.assumeTrue(requiresAnyCoreLibDesugaring(parameters));
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    Path dumpDir = temp.newFolder().toPath();
    testForD8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(options -> options.dumpInputToDirectory = dumpDir.toString())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            (minAPILevel, keepRules, shrink) ->
                this.buildDesugaredLibrary(
                    minAPILevel,
                    keepRules,
                    shrink,
                    ImmutableList.of(),
                    opt -> opt.dumpInputToDirectory = dumpDir.toString()),
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("PT42S");
    verifyDumpDir(dumpDir);
  }

  @Test
  public void testR8DumpToDirectory() throws Exception {
    Assume.assumeTrue(requiresAnyCoreLibDesugaring(parameters));
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    Path dumpDir = temp.newFolder().toPath();
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(options -> options.dumpInputToDirectory = dumpDir.toString())
        .allowDiagnosticInfoMessages()
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            (minAPILevel, keepRules, shrink) ->
                this.buildDesugaredLibrary(
                    minAPILevel,
                    keepRules,
                    shrink,
                    ImmutableList.of(),
                    opt -> opt.dumpInputToDirectory = dumpDir.toString()),
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .assertAllInfoMessagesMatch(containsString("Dumped compilation inputs to:"))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("PT42S");
    verifyDumpDir(dumpDir);
  }

  private void verifyDumpDir(Path dumpDir) throws IOException {
    assertTrue(Files.isDirectory(dumpDir));
    List<Path> paths = Files.walk(dumpDir, 1).collect(Collectors.toList());
    assertEquals(3, paths.size());
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
    assertTrue(Files.exists(unzipped.resolve("build.properties")));
  }

  static class TestClass {
    public static void main(String[] args) {
      System.out.println(Duration.ofSeconds(42));
    }
  }
}
