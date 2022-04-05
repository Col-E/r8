// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceWhitespaceTest extends TestBase {

  private final TestParameters parameters;

  private final String MAPPING =
      StringUtils.lines(
          " foo -> bar:", "void someMethod() -> a", " baz -> qux:", "void someOtherMethod() -> b");
  private final List<String> STACKTRACE =
      ImmutableList.of(" at bar.a(SourceFile)", " at qux.b(SourceFile)");
  private final String EXPECTED =
      StringUtils.lines(" at foo.someMethod(foo.java)", " at baz.someOtherMethod(baz.java)");

  private Path mappingFile;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetraceWhitespaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Before
  public void before() throws Exception {
    mappingFile = temp.newFile("mapping.txt").toPath();
    Files.write(mappingFile, MAPPING.getBytes());
  }

  @Test
  public void testR8Retrace() {
    // TODO(b/228154323): R8 Retrace should not be white space sensitive.
    assertThrows(
        InvalidMappingFileException.class,
        () ->
            Retrace.run(
                RetraceCommand.builder()
                    .setProguardMapProducer(ProguardMapProducer.fromPath(mappingFile))
                    .setStackTrace(STACKTRACE)
                    .setRetracedStackTraceConsumer(lines -> {})
                    .build()));
  }

  @Test
  public void testPGRetrace() throws Exception {
    Path stackTraceFile = temp.newFile("stacktrace.txt").toPath();
    Files.write(stackTraceFile, StringUtils.joinLines(STACKTRACE).getBytes(StandardCharsets.UTF_8));
    List<String> command = new ArrayList<>();
    command.add(ProguardVersion.V7_0_0.getRetraceScript().toString());
    command.add(mappingFile.toString());
    command.add(stackTraceFile.toString());
    ProcessBuilder builder = new ProcessBuilder(command);
    ProcessResult processResult = ToolHelper.runProcess(builder);
    assertEquals(0, processResult.exitCode);
    assertEquals(EXPECTED, processResult.stdout);
  }
}
