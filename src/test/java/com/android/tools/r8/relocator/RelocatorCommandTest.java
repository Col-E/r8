// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.relocator;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.PackageReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RelocatorCommandTest extends TestBase {

  private static final PackageReference SOURCE = Reference.packageFromString("foo");
  private static final PackageReference DESTINATION = Reference.packageFromString("bar");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RelocatorCommandTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testCommandBuilder() throws CompilationFailedException, IOException {
    Path input1 = temp.newFile("in1.jar").toPath();
    Path input2 = temp.newFile("in2.jar").toPath();
    Path output = temp.newFile("output.jar").toPath();
    RelocatorCommand command =
        RelocatorCommand.builder()
            .setThreadCount(42)
            .setOutputPath(output)
            .addProgramFiles(input1, input2)
            .addPackageMapping(SOURCE, DESTINATION)
            .build();
    assertEquals(42, command.getThreadCount());
    assertNotNull(command.getMapping());
    assertEquals(DESTINATION, command.getMapping().getPackageMappings().get(SOURCE));
    List<ProgramResourceProvider> programResources = command.getApp().getProgramResourceProviders();
    assertEquals(2, programResources.size());
    for (ProgramResourceProvider programResourceProvider : programResources) {
      assertTrue(programResourceProvider instanceof ArchiveResourceProvider);
    }
  }

  @Test
  public void testPrint() throws CompilationFailedException {
    RelocatorCommand.Builder builder =
        RelocatorCommand.builder().setPrintHelp(true).setPrintVersion(true);
    RelocatorCommand command = builder.build();
    assertTrue(command.isPrintHelp());
    assertTrue(command.isPrintVersion());
    assertNull(command.getApp());
    assertNull(command.getMapping());
  }

  @Test
  public void testParser() {}

  @Test
  public void testInvalidThreadCount() {
    CompilationFailedException exception =
        assertThrows(
            CompilationFailedException.class,
            () -> {
              Path input1 = temp.newFile("in1.jar").toPath();
              Path input2 = temp.newFile("in2.jar").toPath();
              Path output = temp.newFile("output.jar").toPath();
              RelocatorCommand.builder()
                  .setThreadCount(-2)
                  .setOutputPath(output)
                  .addProgramFiles(input1, input2)
                  .addPackageMapping(SOURCE, DESTINATION)
                  .build();
            });
    assertThat(exception.getCause().getMessage(), containsString("Invalid threadCount: -2"));
  }

  @Test
  public void testUnknownArgument() {
    CompilationFailedException exception =
        assertThrows(
            CompilationFailedException.class,
            () ->
                RelocatorCommand.parse(new String[] {"--unknown-argument"}, Origin.unknown())
                    .build());
    assertThat(
        exception.getCause().getMessage(), containsString("Unknown argument: --unknown-argument"));
  }

  @Test
  public void testDuplicateOutputPaths() {
    CompilationFailedException exception =
        assertThrows(
            CompilationFailedException.class,
            () -> {
              Path input1 = temp.newFile("in.jar").toPath();
              RelocatorCommand.parse(
                      new String[] {
                        "--output",
                        "first_output",
                        "--output",
                        "another_output",
                        "--map",
                        "foo->bar",
                        "--input",
                        input1.toString()
                      },
                      Origin.unknown())
                  .build();
            });
    assertThat(
        exception.getCause().getMessage(),
        containsString("Cannot output both to 'first_output' and 'another_output'"));
  }

  @Test
  public void testNoOutput() {
    CompilationFailedException exception =
        assertThrows(
            CompilationFailedException.class,
            () -> {
              Path input1 = temp.newFile("in.jar").toPath();
              RelocatorCommand.builder()
                  .addProgramFile(input1)
                  .addPackageMapping(SOURCE, DESTINATION)
                  .build();
            });
    assertThat(
        exception.getCause().getMessage(),
        containsString("No output path or consumer has been specified"));
  }

  @Test
  public void testConsumer() throws IOException, CompilationFailedException {
    Path input = temp.newFile("input.jar").toPath();
    Path output = temp.newFile("output.jar").toPath();
    ArchiveConsumer programConsumer = new ArchiveConsumer(output);
    RelocatorCommand command =
        RelocatorCommand.builder()
            .setThreadCount(42)
            .addProgramFiles(input)
            .setConsumer(programConsumer)
            .addPackageMapping(SOURCE, DESTINATION)
            .build();
    assertEquals(command.getConsumer(), programConsumer);
  }

  @Test
  public void testInvalidPackage() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              Path input1 = temp.newFile("in1.jar").toPath();
              RelocatorCommand.parse(
                      new String[] {
                        "--output",
                        "output",
                        "--map",
                        "invalid;package-name.*->bar",
                        "--input",
                        input1.toString()
                      },
                      Origin.unknown())
                  .build();
            });
    assertThat(
        exception.getMessage(), containsString("Package name 'invalid;package-name' is not valid"));
  }
}
