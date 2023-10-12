// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.partition;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.retrace.Retrace;
import com.android.tools.r8.retrace.RetraceCommand;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.PartitionMapZipContainer;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Test to ensure that old formats of the partitioned mapping file continue to work. */
@RunWith(Parameterized.class)
public class RetracePartitionFormatsTest extends TestBase {

  private static final List<String> MAPPING_DIRECTORIES = ImmutableList.of("20231012");

  @Parameters(name = "{1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), MAPPING_DIRECTORIES);
  }

  private final String directory;

  public RetracePartitionFormatsTest(TestParameters parameters, String directory) {
    parameters.assertNoneRuntime();
    this.directory = directory;
  }

  private static Path getPartitionDataRoot() {
    return Paths.get(ToolHelper.THIRD_PARTY_DIR, "retrace", "partition_formats");
  }

  private static Path getRetracedStacktracePath(Path directory) {
    return directory.resolve("retraced-stacktrace.txt");
  }

  private static Path getRawStacktracePath(Path directory) {
    return directory.resolve("raw-stacktrace.txt");
  }

  private static Path getPartitionedMapPath(Path directory) {
    return directory.resolve("partitioned-map.zip");
  }

  private Path getDirectoryPath() {
    return getPartitionDataRoot().resolve(directory);
  }

  @Test
  public void test() throws Exception {
    Path directory = getDirectoryPath();
    Path mapping = getPartitionedMapPath(directory);
    List<String> expected = FileUtils.readAllLines(getRetracedStacktracePath(directory));
    retrace(
        mapping,
        getRawStacktracePath(directory),
        retraced -> assertEquals(String.join("\n", expected), String.join("\n", retraced)));
  }

  private static void retrace(Path mapping, Path stacktrace, Consumer<List<String>> retraced)
      throws Exception {
    Retrace.run(
        RetraceCommand.builder()
            .setMappingSupplier(
                PartitionMapZipContainer.createPartitionMapZipContainerSupplier(mapping))
            .setStackTrace(FileUtils.readAllLines(stacktrace))
            .setRetracedStackTraceConsumer(retraced)
            .build());
  }

  /**
   * Generate a snapshot of the R8 partitioned mapping file.
   *
   * <p>NOTE: Remember to compile r8lib before running this command!
   */
  public static void main(String[] args) throws Exception {
    // Run r8lib with r8lib as input and force it to throw an exception.
    ProcessResult result =
        ToolHelper.runJava(
            CfRuntime.getSystemRuntime(),
            ImmutableList.of("-Dcom.android.tools.r8.testing.forceThrowInConvert"),
            ImmutableList.of(ToolHelper.R8LIB_JAR),
            "com.android.tools.r8.R8",
            "--no-tree-shaking",
            "--lib",
            ToolHelper.getJava8RuntimeJar().toString(),
            ToolHelper.R8LIB_JAR.toString());
    assertEquals("Expected compilation to fail: " + result, result.exitCode, 1);

    LocalDate now = LocalDate.now();
    String datePrefix = "" + now.getYear() + now.getMonthValue() + now.getDayOfMonth();
    Path directory = getPartitionDataRoot().resolve(datePrefix);
    Path mapping = getPartitionedMapPath(directory);
    Path rawStacktrace = getRawStacktracePath(directory);
    Path retracedStacktrace = getRetracedStacktracePath(directory);

    Files.createDirectory(directory);
    Files.copy(ToolHelper.R8LIB_MAP_PARTITIONED, mapping);
    FileUtils.writeTextFile(rawStacktrace, result.stderr);
    retrace(
        mapping,
        rawStacktrace,
        retraced -> {
          try {
            FileUtils.writeTextFile(retracedStacktrace, retraced);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
    System.out.println("Generated new testing files in: " + directory);

    // Check that the raw and retraced files looks reasonable.
    String raw = FileUtils.readTextFile(rawStacktrace, StandardCharsets.UTF_8);
    String retraced = FileUtils.readTextFile(retracedStacktrace, StandardCharsets.UTF_8);
    // Both files should throw the expected error.
    assertThat(raw, containsString("Forcing compilation failure for testing"));
    assertThat(retraced, containsString("Forcing compilation failure for testing"));
    // The raw file should have R8 source file markers and the retraced file should not.
    assertThat(raw, containsString("(R8_"));
    assertThat(retraced, not(containsString("(R8_")));

    System.out.println("==========================");
    System.out.println("Remember to upload changes to cloud storage:");
    System.out.println(
        "(cd "
            + getPartitionDataRoot().getParent()
            + "; upload_to_google_storage.py -a --bucket r8-deps partition_formats)");
    System.out.println("==========================");
  }
}
