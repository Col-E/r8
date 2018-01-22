// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.GenerateMainDexListCommand;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.ToolHelper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class GenerateMainDexListCommandTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  private Path getOutputPath(GenerateMainDexListCommand command) {
    StringConsumer consumer = command.getMainDexListConsumer();
    if (consumer instanceof StringConsumer.FileConsumer) {
      return ((StringConsumer.FileConsumer) consumer).getOutputPath();
    }
    return null;
  }

  @Test
  public void emptyCommand() throws Throwable {
    verifyEmptyCommand(GenerateMainDexListCommand.builder().build());
    verifyEmptyCommand(parse());
    verifyEmptyCommand(parse(""));
    verifyEmptyCommand(parse("", ""));
    verifyEmptyCommand(parse(" "));
    verifyEmptyCommand(parse(" ", " "));
    verifyEmptyCommand(parse("\t"));
    verifyEmptyCommand(parse("\t", "\t"));
  }

  private void verifyEmptyCommand(GenerateMainDexListCommand command) throws IOException {
    assertEquals(0, ToolHelper.getApp(command).getDexProgramResourcesForTesting().size());
    assertEquals(0, ToolHelper.getApp(command).getClassProgramResourcesForTesting().size());
    assertFalse(ToolHelper.getApp(command).hasMainDexListResources());
  }

  private void addAndroidJarsToCommandLine(List<String> args) {
    args.add("--lib");
    args.add(ToolHelper.getAndroidJar(AndroidApiLevel.K.getLevel()).toAbsolutePath().toString());
  }

  // Add the jars used in the com.android.tools.r8.maindexlist.MainDexTracingTest test.
  private void addInputJarsToCommandLine(List<String> args) {
    args.add(Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "multidex001" + JAR_EXTENSION)
        .toAbsolutePath().toString());
    args.add(Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "multidexfakeframeworks" + JAR_EXTENSION)
        .toAbsolutePath().toString());
  }

  // Add main-dex rules used in the com.android.tools.r8.maindexlist.MainDexTracingTest test.
  private void addMainDexRuleToCommandLine(List<String> args) {
    args.add("--main-dex-rules");
    args.add(Paths.get(ToolHelper.EXAMPLES_DIR, "multidex", "main-dex-rules.txt")
        .toAbsolutePath().toString());
  }

  @Test
  public void defaultOutIsCwd() throws Throwable {
    Path working = temp.getRoot().toPath();
    String mainDexListOutput = "main-dex-list.txt";
    Path output = working.resolve(mainDexListOutput);
    assertFalse(Files.exists(output));
    List<String> args = new ArrayList<>();
    addAndroidJarsToCommandLine(args);
    addInputJarsToCommandLine(args);
    addMainDexRuleToCommandLine(args);
    assertEquals(0, ToolHelper.forkGenerateMainDexList(
        working, args, "--main-dex-list-output", mainDexListOutput).exitCode);
    assertTrue(Files.exists(output));
    assertTrue(Files.size(output) > 0);
  }

  @Test
  public void validOutputPath() throws Throwable {
    Path existingFile = temp.getRoot().toPath().resolve("existing_output");
    try (OutputStream existingFileOut = Files.newOutputStream(existingFile,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
      PrintWriter writer = new PrintWriter(existingFileOut);
      writer.println("Hello, world!");
      writer.flush();
    }
    Path nonExistingFile = temp.getRoot().toPath().resolve("non_existing_output");
    assertEquals(
        existingFile,
        getOutputPath(
            GenerateMainDexListCommand.builder().setMainDexListOutputPath(existingFile).build()));
    assertEquals(
        nonExistingFile,
        getOutputPath(
            GenerateMainDexListCommand.builder()
                .setMainDexListOutputPath(nonExistingFile).build()));
    assertEquals(
        existingFile,
        getOutputPath(parse("--main-dex-list-output", existingFile.toString())));
    assertEquals(
        nonExistingFile,
        getOutputPath(parse("--main-dex-list-output", nonExistingFile.toString())));
  }

  @Test
  public void nonExistingOutputFileInNonExistingDir() throws Throwable {
    Path nonExistingFileInNonExistingDir =
        temp.getRoot().toPath().resolve("a/path/that/does/not/exist");
    assertEquals(
        nonExistingFileInNonExistingDir,
        getOutputPath(
            GenerateMainDexListCommand.builder()
                .setMainDexListOutputPath(nonExistingFileInNonExistingDir).build()));
    assertEquals(
        nonExistingFileInNonExistingDir,
        getOutputPath(
            parse("--main-dex-list-output", nonExistingFileInNonExistingDir.toString())));
  }

  @Test
  public void mainDexRules() throws Throwable {
    Path mainDexRules1 = temp.newFile("main-dex-1.rules").toPath();
    Path mainDexRules2 = temp.newFile("main-dex-2.rules").toPath();
    parse("--main-dex-rules", mainDexRules1.toString());
    parse(
        "--main-dex-rules", mainDexRules1.toString(), "--main-dex-rules", mainDexRules2.toString());
  }

  @Test
  public void mainDexList() throws Throwable {
    Path mainDexList1 = temp.newFile("main-dex-list-1.txt").toPath();
    Path mainDexList2 = temp.newFile("main-dex-list-2.txt").toPath();
    parse("--main-dex-list", mainDexList1.toString());
    parse("--main-dex-list", mainDexList1.toString(), "--main-dex-list", mainDexList2.toString());
  }

  private GenerateMainDexListCommand parse(String... args) throws Throwable {
    return GenerateMainDexListCommand.parse(args).build();
  }
}
