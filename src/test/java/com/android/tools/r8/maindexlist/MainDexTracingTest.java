// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.ZIP_EXTENSION;

import com.android.tools.r8.GenerateMainDexList;
import com.android.tools.r8.GenerateMainDexListCommand;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ir.desugar.LambdaRewriter;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        AndroidApiLevel.I);
  }

  @Test
  public void traceMainDexList001_2() throws Throwable {
    doTest(
        "traceMainDexList001_2",
        "multidex001",
        EXAMPLE_BUILD_DIR,
        Paths.get(EXAMPLE_SRC_DIR, "multidex001", "main-dex-rules-2.txt"),
        Paths.get(EXAMPLE_SRC_DIR, "multidex001", "ref-list-2.txt"),
        AndroidApiLevel.I);
  }

  @Test
  public void traceMainDexList002() throws Throwable {
    doTest(
        "traceMainDexList002",
        "multidex002",
        EXAMPLE_BUILD_DIR,
        Paths.get(EXAMPLE_SRC_DIR, "multidex", "main-dex-rules.txt"),
        Paths.get(EXAMPLE_SRC_DIR, "multidex002", "ref-list-1.txt"),
        AndroidApiLevel.I);
  }

  @Test
  public void traceMainDexList003() throws Throwable {
    doTest(
        "traceMainDexList003",
        "multidex003",
        EXAMPLE_BUILD_DIR,
        Paths.get(EXAMPLE_SRC_DIR, "multidex", "main-dex-rules.txt"),
        Paths.get(EXAMPLE_SRC_DIR, "multidex003", "ref-list-1.txt"),
        AndroidApiLevel.I);
  }

  @Test
  public void traceMainDexList004() throws Throwable {
    doTest(
        "traceMainDexList004",
        "multidex004",
        EXAMPLE_O_BUILD_DIR,
        Paths.get(EXAMPLE_SRC_DIR, "multidex", "main-dex-rules.txt"),
        Paths.get(EXAMPLE_O_SRC_DIR, "multidex004", "ref-list-1.txt"),
        AndroidApiLevel.I);
  }

  @Test
  public void traceMainDexList005_1() throws Throwable {
    doTest5(1);
  }

  @Test
  public void traceMainDexList005_2() throws Throwable {
    doTest5(2);
  }

  @Test
  public void traceMainDexList005_3() throws Throwable {
    doTest5(3);
  }

  @Test
  public void traceMainDexList005_4() throws Throwable {
    doTest5(4);
  }

  @Test
  public void traceMainDexList005_5() throws Throwable {
    doTest5(5);
  }

  @Test
  public void traceMainDexList005_6() throws Throwable {
    doTest5(6);
  }

  @Test
  public void traceMainDexList005_7() throws Throwable {
    doTest5(7);
  }

  @Test
  public void traceMainDexList006() throws Throwable {
    doTest(
        "traceMainDexList006",
        "multidex006",
        EXAMPLE_BUILD_DIR,
        Paths.get(EXAMPLE_SRC_DIR, "multidex006", "main-dex-rules-1.txt"),
        Paths.get(EXAMPLE_SRC_DIR, "multidex006", "ref-list-1.txt"),
        AndroidApiLevel.I);
  }

  private void doTest5(int variant) throws Throwable {
    doTest(
        "traceMainDexList005",
        "multidex005",
        EXAMPLE_BUILD_DIR,
        Paths.get(EXAMPLE_SRC_DIR, "multidex005", "main-dex-rules-" + variant + ".txt"),
        Paths.get(EXAMPLE_SRC_DIR, "multidex005", "ref-list-" + variant + ".txt"),
        AndroidApiLevel.I);
  }

  private void doTest(
      String testName,
      String packageName,
      String buildDir,
      Path mainDexRules,
      Path expectedMainDexList,
      AndroidApiLevel minSdk)
      throws Throwable {
    doTest(
        testName,
        packageName,
        buildDir,
        mainDexRules,
        expectedMainDexList,
        minSdk,
        (options) -> {
          options.enableInlining = false;
        });
  }

  private void doTest(
      String testName,
      String packageName,
      String buildDir,
      Path mainDexRules,
      Path expectedMainDexList,
      AndroidApiLevel minSdk,
      Consumer<InternalOptions> optionsConsumer)
      throws Throwable {
    Path out = temp.getRoot().toPath().resolve(testName + ZIP_EXTENSION);

    Path inputJar = Paths.get(buildDir, packageName + JAR_EXTENSION);
    try {
      // Build main-dex list using GenerateMainDexList and test the output from run.
      GenerateMainDexListCommand.Builder mdlCommandBuilder = GenerateMainDexListCommand.builder();
      GenerateMainDexListCommand mdlCommand = mdlCommandBuilder
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.K))
          .addProgramFiles(inputJar)
          .addProgramFiles(Paths.get(EXAMPLE_BUILD_DIR, "multidexfakeframeworks" + JAR_EXTENSION))
          .addMainDexRulesFiles(mainDexRules)
          .build();
      List<String> mainDexGeneratorMainDexList =
          GenerateMainDexList.run(mdlCommand).stream()
              .map(this::mainDexStringToDescriptor)
              .sorted()
              .collect(Collectors.toList());

      class Box {
        String content;
      }

      // Build main-dex list using GenerateMainDexList and test the output from a consumer.
      final Box mainDexListOutput = new Box();
      mdlCommandBuilder = GenerateMainDexListCommand.builder();
      mdlCommand = mdlCommandBuilder
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.K))
          .addProgramFiles(inputJar)
          .addProgramFiles(Paths.get(EXAMPLE_BUILD_DIR, "multidexfakeframeworks" + JAR_EXTENSION))
          .addMainDexRulesFiles(mainDexRules)
          .setMainDexListConsumer((string, handler) -> mainDexListOutput.content = string)
          .build();
      GenerateMainDexList.run(mdlCommand);
      List<String> mainDexGeneratorMainDexListFromConsumer =
          StringUtils.splitLines(mainDexListOutput.content).stream()
              .map(this::mainDexStringToDescriptor)
              .sorted()
              .collect(Collectors.toList());

      // Build main-dex list using R8.
      final Box r8MainDexListOutput = new Box();
      R8Command.Builder r8CommandBuilder = R8Command.builder();
      R8Command command =
          r8CommandBuilder
              .setMinApiLevel(minSdk.getLevel())
              .addProgramFiles(inputJar)
              .addProgramFiles(
                  Paths.get(EXAMPLE_BUILD_DIR, "multidexfakeframeworks" + JAR_EXTENSION))
              .addLibraryFiles(ToolHelper.getAndroidJar(minSdk))
              .setOutput(out, OutputMode.DexIndexed)
              .addMainDexRulesFiles(mainDexRules)
              .setMainDexListConsumer((string, handler) -> r8MainDexListOutput.content = string)
              .build();
      ToolHelper.runR8WithFullResult(command, optionsConsumer);
      List<String> r8MainDexList =
          StringUtils.splitLines(r8MainDexListOutput.content).stream()
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
        if (r8MainDexList.size() <= i) {
          Assert.fail("R8 main dex list is missing '" + reference + "'");
        }
        checkSameMainDexEntry(reference, r8MainDexList.get(i));
        // The main dex list generator does not do any lambda desugaring.
        if (!isLambda(reference)) {
          if (mainDexGeneratorMainDexList.size() <= i - nonLambdaOffset) {
            Assert.fail("Main dex list generator is missing '" + reference + "'");
          }
          checkSameMainDexEntry(reference, mainDexGeneratorMainDexList.get(i - nonLambdaOffset));
          checkSameMainDexEntry(
              reference, mainDexGeneratorMainDexListFromConsumer.get(i - nonLambdaOffset));
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
