// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.ZIP_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.withNativeFileSeparators;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.GenerateMainDexList;
import com.android.tools.r8.GenerateMainDexListCommand;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.WhyAreYouKeepingConsumer;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MainDexTracingTest extends TestBase {

  private static final String EXAMPLE_BUILD_DIR = ToolHelper.EXAMPLES_BUILD_DIR;
  private static final String EXAMPLE_O_BUILD_DIR = ToolHelper.EXAMPLES_ANDROID_O_BUILD_DIR;
  private static final String EXAMPLE_SRC_DIR = ToolHelper.EXAMPLES_DIR;
  private static final String EXAMPLE_O_SRC_DIR = ToolHelper.EXAMPLES_ANDROID_O_DIR;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private final Backend backend = Backend.CF;

  public MainDexTracingTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private Path getInputJar(Path cfJar) throws Exception {
    if (backend == Backend.CF) {
      return cfJar;
    }
    return testForD8()
        .setIntermediate(true)
        .addProgramFiles(cfJar)
        .setMinApi(AndroidApiLevel.K)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.K))
        .compile()
        .writeToZip();
  }

  @Test
  public void traceMainDexList001_whyareyoukeeping() throws Throwable {
    PrintStream stdout = System.out;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    System.setOut(new PrintStream(baos));
    doTest(
        "traceMainDexList001_1",
        "multidex001",
        EXAMPLE_BUILD_DIR,
        Paths.get(EXAMPLE_SRC_DIR, "multidex", "main-dex-rules-whyareyoukeeping.txt"),
        Paths.get(EXAMPLE_SRC_DIR, "multidex001", "ref-list-1.txt"),
        Paths.get(EXAMPLE_SRC_DIR, "multidex001", "ref-list-1.txt"),
        AndroidApiLevel.I,
        TestCompilerBuilder::allowStdoutMessages);
    String output = new String(baos.toByteArray(), Charset.defaultCharset());
    assertThat(output, containsString("is referenced in keep rule:"));
    System.setOut(stdout);
  }

  @Test
  public void traceMainDexList001_whyareyoukeeping_consumer() throws Throwable {
    WhyAreYouKeepingConsumer graphConsumer = new WhyAreYouKeepingConsumer(null);
    doTest(
        "traceMainDexList001_1",
        "multidex001",
        EXAMPLE_BUILD_DIR,
        Paths.get(EXAMPLE_SRC_DIR, "multidex", "main-dex-rules.txt"),
        Paths.get(EXAMPLE_SRC_DIR, "multidex001", "ref-list-1.txt"),
        Paths.get(EXAMPLE_SRC_DIR, "multidex001", "ref-list-1.txt"),
        AndroidApiLevel.I,
        builder ->
            builder.addOptionsModification(
                options -> {
                  options.inlinerOptions().enableInlining = false;
                  options.mainDexKeptGraphConsumer = graphConsumer;
                }));
    String root = ToolHelper.getProjectRoot();
    {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      graphConsumer.printWhyAreYouKeeping(
          Reference.classFromTypeName("multidex001.MainActivity"), new PrintStream(baos));
      String output = new String(baos.toByteArray(), Charset.defaultCharset());
      String expected =
          StringUtils.lines(
              "multidex001.MainActivity",
              "|- is referenced in keep rule:",
              withNativeFileSeparators(
                  "|  " + root + "src/test/examples/multidex/main-dex-rules.txt:14:1"));
      assertEquals(expected, output);
    }
    {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      graphConsumer.printWhyAreYouKeeping(
          Reference.classFromTypeName("multidex001.ClassForMainDex"), new PrintStream(baos));
      String output = new String(baos.toByteArray(), Charset.defaultCharset());
      // TODO(b/120951570): We should be able to get the reason for ClassForMainDex too.
      String expected =
          true
              ? StringUtils.lines("Nothing is keeping multidex001.ClassForMainDex")
              : StringUtils.lines(
                  "multidex001.ClassForMainDex",
                  "|- is direct reference from:",
                  "|  multidex001.MainActivity",
                  "|- is referenced in keep rule:",
                  withNativeFileSeparators(
                      "|  " + root + "src/test/examples/multidex/main-dex-rules.txt:14:1"));
      assertEquals(expected, output);
    }
  }

  @Test
  public void traceMainDexList001_1() throws Throwable {
    doTest(
        "traceMainDexList001_1",
        "multidex001",
        EXAMPLE_BUILD_DIR,
        Paths.get(EXAMPLE_SRC_DIR, "multidex", "main-dex-rules.txt"),
        Paths.get(EXAMPLE_SRC_DIR, "multidex001", "ref-list-1.txt"),
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
        Paths.get(EXAMPLE_O_SRC_DIR, "multidex004", "ref-list-1.txt"),
        AndroidApiLevel.I,
        builder ->
            builder.addOptionsModification(
                options -> options.inlinerOptions().enableInlining = false));
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
    doTest(
        "traceMainDexList005",
        "multidex005",
        EXAMPLE_BUILD_DIR,
        Paths.get(EXAMPLE_SRC_DIR, "multidex005", "main-dex-rules-4.txt"),
        Paths.get(EXAMPLE_SRC_DIR, "multidex005", "ref-list-4-r8.txt"),
        Paths.get(EXAMPLE_SRC_DIR, "multidex005", "ref-list-4.txt"),
        AndroidApiLevel.I);
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
  public void traceMainDexList005_8() throws Throwable {
    doTest5(8);
  }

  @Test
  public void traceMainDexList005_9() throws Throwable {
    doTest5(9);
  }

  @Test
  public void traceMainDexList005_10() throws Throwable {
    doTest5(10);
  }

  @Test
  public void traceMainDexList006() throws Throwable {
    doTest(
        "traceMainDexList006",
        "multidex006",
        EXAMPLE_BUILD_DIR,
        Paths.get(EXAMPLE_SRC_DIR, "multidex006", "main-dex-rules-1.txt"),
        Paths.get(EXAMPLE_SRC_DIR, "multidex006", "ref-list-1.txt"),
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
        Paths.get(EXAMPLE_SRC_DIR, "multidex005", "ref-list-" + variant + ".txt"),
        AndroidApiLevel.I);
  }

  private void doTest(
      String testName,
      String packageName,
      String buildDir,
      Path mainDexRules,
      Path expectedR8MainDexList,
      Path expectedMainDexList,
      AndroidApiLevel minSdk)
      throws Throwable {
    doTest(
        testName,
        packageName,
        buildDir,
        mainDexRules,
        expectedR8MainDexList,
        expectedMainDexList,
        minSdk,
        builder ->
            builder.addOptionsModification(
                options -> options.inlinerOptions().enableInlining = false));
  }

  private void doTest(
      String testName,
      String packageName,
      String buildDir,
      Path mainDexRules,
      Path expectedR8MainDexList,
      Path expectedMainDexList,
      AndroidApiLevel minSdk,
      ThrowableConsumer<R8FullTestBuilder> configuration)
      throws Throwable {
    Path out = temp.getRoot().toPath().resolve(testName + ZIP_EXTENSION);

    Path inputJar = getInputJar(Paths.get(buildDir, packageName + JAR_EXTENSION));
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

    // Build main-dex list using GenerateMainDexList and test the output from a consumer.
    final Box<String> mainDexListOutput = new Box<>();
    mdlCommandBuilder = GenerateMainDexListCommand.builder();
    mdlCommand =
        mdlCommandBuilder
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.K))
            .addProgramFiles(inputJar)
            .addProgramFiles(Paths.get(EXAMPLE_BUILD_DIR, "multidexfakeframeworks" + JAR_EXTENSION))
            .addMainDexRulesFiles(mainDexRules)
            .setMainDexListConsumer(ToolHelper.consumeString(mainDexListOutput::set))
            .build();
    GenerateMainDexList.run(mdlCommand);
    List<String> mainDexGeneratorMainDexListFromConsumer =
        StringUtils.splitLines(mainDexListOutput.get()).stream()
            .map(this::mainDexStringToDescriptor)
            .sorted()
            .collect(Collectors.toList());

    // Build main-dex list using D8 & rules.
    List<String> mainDexListFromD8;
    {
      final Box<String> mainDexListOutputFromD8 = new Box<>();
      testForD8(Backend.DEX)
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.K))
          .addProgramFiles(inputJar)
          .addProgramFiles(Paths.get(EXAMPLE_BUILD_DIR, "multidexfakeframeworks" + JAR_EXTENSION))
          .addMainDexRulesFiles(mainDexRules)
          .setMainDexListConsumer(ToolHelper.consumeString(mainDexListOutputFromD8::set))
          .setMinApi(minSdk)
          .allowStdoutMessages()
          .compile();
      mainDexListFromD8 =
          StringUtils.splitLines(mainDexListOutputFromD8.get()).stream()
              .map(this::mainDexStringToDescriptor)
              .sorted()
              .collect(Collectors.toList());
    }

    // Build main-dex list using R8.
    final Box<String> r8MainDexListOutput = new Box<>();
    testForR8(Backend.DEX)
        .addProgramFiles(inputJar)
        .addProgramFiles(Paths.get(EXAMPLE_BUILD_DIR, "multidexfakeframeworks" + JAR_EXTENSION))
        .addKeepRules("-keepattributes *Annotation*")
        .addMainDexRuleFiles(mainDexRules)
        .apply(configuration)
        .assumeAllMethodsMayHaveSideEffects()
        .setMinApi(minSdk)
        .addDontObfuscate()
        .noTreeShaking()
        .addDontOptimize()
        .setMainDexListConsumer(ToolHelper.consumeString(r8MainDexListOutput::set))
        .allowDiagnosticMessages()
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertNoInfos().assertNoErrors();
              if (backend == Backend.CF) {
                diagnostics.assertWarningsMatch(
                    diagnosticMessage(equalTo("Resource 'META-INF/MANIFEST.MF' already exists.")));
              } else {
                diagnostics.assertNoWarnings();
              }
            })
        .writeToZip(out);

    List<String> r8MainDexList =
        StringUtils.splitLines(r8MainDexListOutput.get()).stream()
            .map(this::mainDexStringToDescriptor)
            .sorted()
            .collect(Collectors.toList());
    // Check that generated lists are the same as the reference list, except for lambda
    // classes which are only produced when running R8.
    String[] r8RefList = new String(Files.readAllBytes(
        expectedR8MainDexList), StandardCharsets.UTF_8).split("\n");
    for (int i = 0; i < r8RefList.length; i++) {
      String reference = r8RefList[i].trim();
      if (r8MainDexList.size() <= i) {
        fail("R8 main dex list is missing '" + reference + "'");
      }
      checkSameMainDexEntry(reference, r8MainDexList.get(i));
    }
    String[] refList = new String(Files.readAllBytes(
        expectedMainDexList), StandardCharsets.UTF_8).split("\n");
    for (int i = 0; i < refList.length; i++) {
      String reference = refList[i].trim();
      if (mainDexListFromD8.size() <= i) {
        fail("D8 main-dex list is missing '" + reference + "'");
      }
      checkSameMainDexEntry(reference, mainDexListFromD8.get(i));
    }
    int nonLambdaOffset = 0;
    for (int i = 0; i < refList.length; i++) {
      String reference = refList[i].trim();
      // The main dex list generator does not do any lambda desugaring.
      if (!isExternalSyntheticLambda(reference)) {
        if (mainDexGeneratorMainDexList.size() <= i - nonLambdaOffset) {
          fail("Main dex list generator is missing '" + reference + "'");
        }
        String fromList = mainDexGeneratorMainDexList.get(i - nonLambdaOffset);
        String fromConsumer = mainDexGeneratorMainDexListFromConsumer.get(i - nonLambdaOffset);
        if (isExternalSyntheticLambda(fromList)) {
          assertEquals(Backend.DEX, backend);
          assertEquals(fromList, fromConsumer);
          nonLambdaOffset--;
        } else {
          checkSameMainDexEntry(reference, fromList);
          checkSameMainDexEntry(reference, fromConsumer);
        }
      } else {
        nonLambdaOffset++;
      }
    }
    testZipfileOrder(out);
  }

  private void testZipfileOrder(Path out) throws IOException {
    // Sanity check that the classes.dex files are in the correct order
    ZipFile outZip = new ZipFile(out.toFile());
    Enumeration<? extends ZipEntry> entries = outZip.entries();
    int index = 0;
    LinkedList<String> entryNames = new LinkedList<>();
    // We expect classes*.dex files first, in order, then the rest of the files, in order.
    while(entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      if (!entry.getName().startsWith("classes") || !entry.getName().endsWith(".dex")) {
        entryNames.add(entry.getName());
        continue;
      }
      if (index == 0) {
        assertEquals("classes.dex", entry.getName());
      } else {
        assertEquals("classes" + (index + 1) + ".dex", entry.getName());
      }
      index++;
    }
    // Everything else should be sorted according to name.
    String[] entriesUnsorted = entryNames.toArray(StringUtils.EMPTY_ARRAY);
    String[] entriesSorted = entryNames.toArray(StringUtils.EMPTY_ARRAY);
    Arrays.sort(entriesSorted);
    assertArrayEquals(entriesUnsorted, entriesSorted);
  }

  private boolean isExternalSyntheticLambda(String mainDexEntry) {
    return SyntheticItemsTestUtils.isExternalLambda(Reference.classFromDescriptor(mainDexEntry));
  }

  private String mainDexStringToDescriptor(String mainDexString) {
    assertTrue(mainDexString.endsWith(FileUtils.CLASS_EXTENSION));
    return DescriptorUtils.getDescriptorFromClassBinaryName(
        mainDexString.substring(0, mainDexString.length() - FileUtils.CLASS_EXTENSION.length()));
  }

  private void checkSameMainDexEntry(String reference, String computed) {
    if (isExternalSyntheticLambda(reference)) {
      // For synthetic classes we check that the context classes match.
      reference = reference.substring(0, reference.lastIndexOf('$'));
      computed = computed.substring(0, computed.lastIndexOf('$'));
    }
    assertEquals(reference, computed);
  }
}
