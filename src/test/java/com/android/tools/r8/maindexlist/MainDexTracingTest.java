// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.ZIP_EXTENSION;

import com.android.tools.r8.GenerateMainDexList;
import com.android.tools.r8.GenerateMainDexListCommand;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ir.desugar.LambdaRewriter;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OutputMode;
import com.android.tools.r8.utils.StringUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MainDexTracingTest {

  private static final String EXAMPLE_BUILD_DIR = ToolHelper.EXAMPLES_BUILD_DIR;
  private static final String EXAMPLE_O_BUILD_DIR = ToolHelper.EXAMPLES_ANDROID_O_BUILD_DIR;
  private static final String EXAMPLE_SRC_DIR = ToolHelper.EXAMPLES_DIR;
  private static final String EXAMPLE_O_SRC_DIR = ToolHelper.EXAMPLES_ANDROID_O_DIR;

  @Rule public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void traceMainDexList001_1() throws Throwable {
    doTest(
        "traceMainDexList001_1",
        "multidex001",
        EXAMPLE_BUILD_DIR,
        Paths.get(EXAMPLE_SRC_DIR, "multidex", "main-dex-rules.txt"),
        Paths.get(EXAMPLE_SRC_DIR, "multidex001", "ref-list-1.txt"),
        AndroidApiLevel.I.getLevel());
  }

  @Test
  public void traceMainDexList001_2() throws Throwable {
    doTest(
        "traceMainDexList001_2",
        "multidex001",
        EXAMPLE_BUILD_DIR,
        Paths.get(EXAMPLE_SRC_DIR, "multidex001", "main-dex-rules-2.txt"),
        Paths.get(EXAMPLE_SRC_DIR, "multidex001", "ref-list-2.txt"),
        AndroidApiLevel.I.getLevel());
  }

  @Test
  public void traceMainDexList002() throws Throwable {
    doTest(
        "traceMainDexList002",
        "multidex002",
        EXAMPLE_BUILD_DIR,
        Paths.get(EXAMPLE_SRC_DIR, "multidex", "main-dex-rules.txt"),
        Paths.get(EXAMPLE_SRC_DIR, "multidex002", "ref-list-1.txt"),
        AndroidApiLevel.I.getLevel());
  }

  @Test
  public void traceMainDexList003() throws Throwable {
    doTest(
        "traceMainDexList003",
        "multidex003",
        EXAMPLE_BUILD_DIR,
        Paths.get(EXAMPLE_SRC_DIR, "multidex", "main-dex-rules.txt"),
        Paths.get(EXAMPLE_SRC_DIR, "multidex003", "ref-list-1.txt"),
        AndroidApiLevel.I.getLevel());
  }

  @Test
  public void traceMainDexList004() throws Throwable {
    doTest(
        "traceMainDexList004",
        "multidex004",
        EXAMPLE_O_BUILD_DIR,
        Paths.get(EXAMPLE_SRC_DIR, "multidex", "main-dex-rules.txt"),
        Paths.get(EXAMPLE_O_SRC_DIR, "multidex004", "ref-list-1.txt"),
        AndroidApiLevel.I.getLevel());
  }

  private void doTest(
      String testName,
      String packageName,
      String buildDir,
      Path mainDexRules,
      Path expectedMainDexList,
      int minSdk)
      throws Throwable {
    doTest(
        testName,
        packageName,
        buildDir,
        mainDexRules,
        expectedMainDexList,
        minSdk,
        (options) -> {
          options.inlineAccessors = false;
        });
  }

  private void doTest(
      String testName,
      String packageName,
      String buildDir,
      Path mainDexRules,
      Path expectedMainDexList,
      int minSdk,
      Consumer<InternalOptions> optionsConsumer)
      throws Throwable {
    Path out = temp.getRoot().toPath().resolve(testName + ZIP_EXTENSION);

    Path inputJar = Paths.get(buildDir, packageName + JAR_EXTENSION);
    try {
      // Build main-dex list using GenerateMainDexList.
      GenerateMainDexListCommand.Builder mdlCommandBuilder = GenerateMainDexListCommand.builder();
      GenerateMainDexListCommand command2 = mdlCommandBuilder
          .addProgramFiles(inputJar)
          .addProgramFiles(Paths.get(EXAMPLE_BUILD_DIR, "multidexfakeframeworks" + JAR_EXTENSION))
          .addMainDexRulesFiles(mainDexRules)
          .build();
      List<String> mainDexGeneratorMainDexList =
          GenerateMainDexList.run(command2).stream()
              .map(this::mainDexStringToDescriptor)
              .sorted()
              .collect(Collectors.toList());

      // Build main-dex list using R8.
      class Box {
        String content;
      }
      final Box r8MainDexListOutput = new Box();
      R8Command.Builder r8CommandBuilder = R8Command.builder();
      R8Command command =
          r8CommandBuilder
              .setMinApiLevel(minSdk)
              .addProgramFiles(inputJar)
              .addProgramFiles(
                  Paths.get(EXAMPLE_BUILD_DIR, "multidexfakeframeworks" + JAR_EXTENSION))
              .addLibraryFiles(Paths.get(ToolHelper.getAndroidJar(minSdk)))
              .setOutput(out, OutputMode.DexIndexed)
              .addMainDexRulesFiles(mainDexRules)
              .setMainDexListConsumer((string, handler) -> r8MainDexListOutput.content = string)
              .build();
      ToolHelper.runR8WithFullResult(command, optionsConsumer);
      List<String> r8MainDexList =
          Arrays.stream(r8MainDexListOutput.content.split(StringUtils.LINE_SEPARATOR))
              .map(this::mainDexStringToDescriptor)
              .sorted()
              .collect(Collectors.toList());
      // Check that both generated lists are the same as the reference list, except for lambda
      // classes which are only produced when running R8.
      String[] refList = new String(Files.readAllBytes(
          expectedMainDexList), StandardCharsets.UTF_8).split("\n");
      int nonLambdaOffset = 0;
      for (int i = 0; i < refList.length; i++) {
        String reference = refList[i].trim();
        checkSameMainDexEntry(reference, r8MainDexList.get(i));
        // The main dex list generator does not do any lambda desugaring.
        if (!isLambda(reference)) {
          checkSameMainDexEntry(reference, mainDexGeneratorMainDexList.get(i - nonLambdaOffset));
        } else {
          nonLambdaOffset++;
        }
      }
    } catch (ExecutionException e) {
      throw e.getCause();
    }
  }

  private boolean isLambda(String mainDexEntry) {
    return mainDexEntry.contains(LambdaRewriter.LAMBDA_CLASS_NAME_PREFIX);
  }

  private String mainDexStringToDescriptor(String mainDexString) {
    final String CLASS_EXTENSION = ".class";
    Assert.assertTrue(mainDexString.endsWith(CLASS_EXTENSION));
    return DescriptorUtils.getDescriptorFromClassBinaryName(
        mainDexString.substring(0, mainDexString.length() - CLASS_EXTENSION.length()));
  }

  private void checkSameMainDexEntry(String reference, String computed) {
    if (isLambda(reference)) {
      // For lambda classes we check that there is a lambda class for the right containing
      // class. However, we do not check the hash for the generated lambda class. The hash
      // changes for different compiler versions because different compiler versions generate
      // different lambda implementation method names.
      reference = reference.substring(0, reference.lastIndexOf('$'));
      computed = computed.substring(0, computed.lastIndexOf('$'));
    }
    Assert.assertEquals(reference, computed);
  }
}
