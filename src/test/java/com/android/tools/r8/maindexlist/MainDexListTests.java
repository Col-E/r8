// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.maindexlist;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.MainDexListParser;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class MainDexListTests extends TestBase {

  private static final int MAX_METHOD_COUNT = Constants.U16BIT_MAX;

  private static final List<String> TWO_LARGE_CLASSES = ImmutableList.of("A", "B");
  private static final int MANY_CLASSES_COUNT = 10000;
  private static final int MANY_CLASSES_SINGLE_DEX_METHODS_PER_CLASS = 2;
  private static final int MANY_CLASSES_MULTI_DEX_METHODS_PER_CLASS = 10;
  private static List<String> MANY_CLASSES;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public MainDexListTests(TestParameters parameters) {
    // We ignore the paramters, but only run once instead of running on every vm
    parameters.assertNoneRuntime();
  }

  interface Runner {
    void run(DiagnosticsHandler handler) throws Throwable;
  }

  @ClassRule
  public static TemporaryFolder generatedApplicationsFolder =
      ToolHelper.getTemporaryFolderForTest();

  // Generate the test applications in a @BeforeClass method, as they are used by several tests.
  @BeforeClass
  public static void generateTestApplications() throws Throwable {
    if (data().stream().count() == 0) {
      return;
    }
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (int i = 0; i < MANY_CLASSES_COUNT; ++i) {
      String pkg = i % 2 == 0 ? "a" : "b";
      builder.add(pkg + ".Class" + i);
    }
    MANY_CLASSES = builder.build();

    // Generates an application with many classes, every even in one package and every odd in
    // another. Keep the number of methods low enough for single dex application.
    generateApplication(
        getManyClassesSingleDexAppPath(), MANY_CLASSES, MANY_CLASSES_SINGLE_DEX_METHODS_PER_CLASS);

    // Generates an application with many classes, every even in one package and every odd in
    // another. Add enough methods so the application cannot fit into one dex file.
    generateApplication(
        getManyClassesMultiDexAppPath(), MANY_CLASSES, MANY_CLASSES_MULTI_DEX_METHODS_PER_CLASS);

    // Generates an application with two classes, each with the maximum possible number of methods.
    generateManyClassesMultiDexApp(getTwoLargeClassesAppPath());
  }

  private static int getLargeClassMethodCount() {
    int otherConstantPoolEntries = 23;
    int maxMethodCount = MAX_METHOD_COUNT - otherConstantPoolEntries;
    return maxMethodCount;
  }

  private static Path getTwoLargeClassesAppPath() {
    return generatedApplicationsFolder.getRoot().toPath().resolve("two-large-classes.zip");
  }

  private static Path getManyClassesSingleDexAppPath() {
    return generatedApplicationsFolder.getRoot().toPath().resolve("many-classes-mono.zip");
  }

  private static Path getManyClassesMultiDexAppPath() {
    return generatedApplicationsFolder.getRoot().toPath().resolve("many-classes-stereo.zip");
  }

  public static void generateManyClassesMultiDexApp(Path path) throws IOException {
    generateApplication(path, TWO_LARGE_CLASSES, getLargeClassMethodCount());
  }

  private static Set<DexType> parse(Path path, DexItemFactory itemFactory) {
    return MainDexListParser.parseList(StringResource.fromFile(path), itemFactory);
  }

  @Test
  public void checkGeneratedFileFitInSingleDexFile() {
    assertTrue(MANY_CLASSES_COUNT * MANY_CLASSES_SINGLE_DEX_METHODS_PER_CLASS <= MAX_METHOD_COUNT);
  }

  @Test
  public void checkGeneratedFileNeedsTwoDexFiles() {
    assertTrue(MANY_CLASSES_COUNT * MANY_CLASSES_MULTI_DEX_METHODS_PER_CLASS > MAX_METHOD_COUNT);
  }

  @Test
  public void putFirstClassInMainDexList() throws Throwable {
    verifyMainDexContains(TWO_LARGE_CLASSES.subList(0, 1), getTwoLargeClassesAppPath(), false);
  }

  @Test
  public void putSecondClassInMainDexList() throws Throwable {
    verifyMainDexContains(TWO_LARGE_CLASSES.subList(1, 2), getTwoLargeClassesAppPath(), false);
  }

  @Test
  public void cannotFitBothIntoMainDex() {
    verifyMainDexContains(
        TWO_LARGE_CLASSES,
        getTwoLargeClassesAppPath(),
        false,
        test -> {
          TestDiagnosticMessagesImpl handler = new TestDiagnosticMessagesImpl();
          try {
            test.run(handler);
            fail("Expect to fail, for there are too many classes for the main-dex list.");
          } catch (Throwable e) {
            assert e instanceof CompilationFailedException;
            handler.assertErrorsMatch(
                DiagnosticsMatcher.diagnosticType(DexFileOverflowDiagnostic.class));
            DexFileOverflowDiagnostic overflow =
                (DexFileOverflowDiagnostic) handler.getErrors().get(0);
            // Make sure {@link MonoDexDistributor} was _not_ used, i.e., a spec was given.
            assertTrue(overflow.hasMainDexSpecification());
            // Make sure what exceeds the limit is the number of methods.
            assertTrue(overflow.getNumberOfMethods() > overflow.getMaximumNumberOfMethods());
            assertEquals(
                TWO_LARGE_CLASSES.size() * getLargeClassMethodCount(),
                overflow.getNumberOfMethods());
          }
        });
  }

  @Test
  public void everyThirdClassInMainDex() throws Throwable {
    ImmutableList.Builder<String> mainDexBuilder = ImmutableList.builder();
    for (int i = 0; i < MANY_CLASSES.size(); i++) {
      String clazz = MANY_CLASSES.get(i);
      if (i % 3 == 0) {
        mainDexBuilder.add(clazz);
      }
    }
    verifyMainDexContains(mainDexBuilder.build(), getManyClassesSingleDexAppPath(), true);
    verifyMainDexContains(mainDexBuilder.build(), getManyClassesMultiDexAppPath(), false);
  }

  @Test
  public void singleClassInMainDex() throws Throwable {
    ImmutableList<String> mainDex = ImmutableList.of(MANY_CLASSES.get(0));
    verifyMainDexContains(mainDex, getManyClassesSingleDexAppPath(), true);
    verifyMainDexContains(mainDex, getManyClassesMultiDexAppPath(), false);
  }

  @Test
  public void allClassesInMainDex() throws Throwable {
    // Degenerated case with an app that fits into a single dex, and where the main dex list
    // contains all classes.
    verifyMainDexContains(MANY_CLASSES, getManyClassesSingleDexAppPath(), true);
  }

  @Test
  public void cannotFitAllIntoMainDex() {
    verifyMainDexContains(
        MANY_CLASSES,
        getManyClassesMultiDexAppPath(),
        false,
        test -> {
          TestDiagnosticMessagesImpl handler = new TestDiagnosticMessagesImpl();
          try {
            test.run(handler);
            fail("Expect to fail, for there are too many classes for the main-dex list.");
          } catch (Throwable e) {
            assert e instanceof CompilationFailedException;
            handler.assertErrorsMatch(
                DiagnosticsMatcher.diagnosticType(DexFileOverflowDiagnostic.class));
            DexFileOverflowDiagnostic overflow =
                (DexFileOverflowDiagnostic) handler.getErrors().get(0);
            // Make sure {@link MonoDexDistributor} was _not_ used, i.e., a main-dex spec was given.
            assertTrue(overflow.hasMainDexSpecification());
            // Make sure what exceeds the limit is the number of methods.
            assertEquals(
                MANY_CLASSES_COUNT * MANY_CLASSES_MULTI_DEX_METHODS_PER_CLASS,
                overflow.getNumberOfMethods());
          }
        });
  }

  @Test
  public void singleEntryNoNewLine() {
    DexItemFactory factory = new DexItemFactory();
    Set<DexType> types =
        MainDexListParser.parseList(
            StringResource.fromString(
                "desugaringwithmissingclasstest1/Main.class", Origin.unknown()),
            factory);
    assertEquals(1, types.size());
    assertEquals(
        "Ldesugaringwithmissingclasstest1/Main;",
        types.iterator().next().toDescriptorString());
  }

  @Test
  public void validEntries() throws IOException {
    List<String> lines = ImmutableList.of("A.class", "a/b/c/D.class", "a/b/c/D$E.class");
    DexItemFactory factory = new DexItemFactory();
    Path mainDexList = temp.getRoot().toPath().resolve("valid.txt");
    FileUtils.writeTextFile(mainDexList, lines);
    Set<DexType> types = parse(mainDexList, factory);
    for (String entry : lines) {
      DexType type = factory.createType("L" + entry.replace(".class", "") + ";");
      assertTrue(types.contains(type));
      assertSame(type, MainDexListParser.parseEntry(entry, factory));
    }
  }

  @Test
  public void leadingBOM() throws IOException {
    List<String> lines =
        ImmutableList.of(StringUtils.BOM + "A.class", "a/b/c/D.class", "a/b/c/D$E.class");
    List<String> classes = ImmutableList.of("A", "a/b/c/D", "a/b/c/D$E");
    DexItemFactory factory = new DexItemFactory();
    Path mainDexList = temp.getRoot().toPath().resolve("valid.txt");
    FileUtils.writeTextFile(mainDexList, lines);
    Set<DexType> types = parse(mainDexList, factory);
    assertEquals(types.size(), classes.size());
    for (String clazz : classes) {
      DexType type = factory.createType("L" + clazz + ";");
      assertTrue(types.contains(type));
    }
  }

  @Test
  public void lotsOfWhitespace() throws IOException {
    List<String> ws =
        ImmutableList.of(
            "",
            " ",
            "  ",
            "\t ",
            " \t",
            "" + StringUtils.BOM,
            StringUtils.BOM + " " + StringUtils.BOM);
    for (String before : ws) {
      for (String after : ws) {
        List<String> lines =
            ImmutableList.of(
                before + "A.class" + after,
                before + "a/b/c/D.class" + after,
                before + "a/b/c/D$E.class" + after,
                before + after);

        List<String> classes = ImmutableList.of("A", "a/b/c/D", "a/b/c/D$E");
        DexItemFactory factory = new DexItemFactory();
        Path mainDexList = temp.getRoot().toPath().resolve("valid.txt");
        FileUtils.writeTextFile(mainDexList, lines);
        Set<DexType> types = parse(mainDexList, factory);
        assertEquals(types.size(), classes.size());
        for (String clazz : classes) {
          DexType type = factory.createType("L" + clazz + ";");
          assertTrue(types.contains(type));
        }
      }
    }
  }

  @Test
  public void validList() throws IOException {
    List<String> list = ImmutableList.of(
        "A.class ",
        " a/b/c/D.class",
        ""
    );
    DexItemFactory factory = new DexItemFactory();
    Path mainDexList = temp.getRoot().toPath().resolve("valid.txt");
    FileUtils.writeTextFile(mainDexList, list);
    Set<DexType> types = parse(mainDexList, factory);
    assertEquals(2, types.size());
  }

  @Test(expected = CompilationError.class)
  public void invalidQualifiedEntry() throws IOException {
    DexItemFactory factory = new DexItemFactory();
    Path mainDexList = temp.getRoot().toPath().resolve("invalid.txt");
    FileUtils.writeTextFile(mainDexList, ImmutableList.of("a.b.c.D.class"));
    parse(mainDexList, factory);
  }

  enum TestMode {
    FROM_CLASS_NAMES,
    FROM_FILE,
    FROM_FILE_WITH_BOM
  }

  private Path runD8WithMainDexList(
      CompilationMode mode, Path input, List<String> mainDexClasses, TestMode testMode)
      throws Exception {
    Path testDir = temp.newFolder().toPath();
    Path listFile = testDir.resolve("main-dex-list.txt");
    if (mainDexClasses != null
        && (testMode == TestMode.FROM_FILE || testMode == TestMode.FROM_FILE_WITH_BOM)) {
      List<String> lines =
          mainDexClasses.stream()
              .map(clazz -> clazz.replace('.', '/') + ".class")
              .collect(Collectors.toList());
      if (testMode == TestMode.FROM_FILE_WITH_BOM) {
        lines.set(0, StringUtils.BOM + lines.get(0));
      }
      FileUtils.writeTextFile(listFile, lines);
    }

    D8Command.Builder builder =
        D8Command.builder()
            .addProgramFiles(input)
            .setMode(mode)
            .setOutput(testDir, OutputMode.DexIndexed);
    if (mainDexClasses != null) {
      if (testMode == TestMode.FROM_FILE) {
        builder.addMainDexListFiles(listFile);
      } else {
        builder.addMainDexClasses(mainDexClasses);
      }
    }
    D8.run(builder.build());
    return testDir;
  }

  private void runDeterministicTest(Path input, List<String> mainDexClasses, boolean allClasses)
      throws Exception {
    // Run test in debug and release mode for minimal vs. non-minimal main-dex.
    for (CompilationMode mode : CompilationMode.values()) {

      // Build with all different main dex lists.
      Map<Path, String> testDirs = new HashMap<>();  // Map Path to test scenario.
      if (allClasses) {
        // If all classes are passed add a run without a main-dex list as well.
        testDirs.put(
            runD8WithMainDexList(mode, input, null, TestMode.FROM_CLASS_NAMES),
            mode.toString() + ": without a main-dex list");
      }
      testDirs.put(
          runD8WithMainDexList(mode, input, mainDexClasses, TestMode.FROM_FILE),
          mode.toString() + ": main-dex list files");
      testDirs.put(
          runD8WithMainDexList(mode, input, mainDexClasses, TestMode.FROM_FILE_WITH_BOM),
          mode.toString() + ": main-dex list files (with BOM)");
      testDirs.put(
          runD8WithMainDexList(mode, input, mainDexClasses, TestMode.FROM_CLASS_NAMES),
          mode.toString() + ": main-dex classes");
      if (mainDexClasses != null) {
        testDirs.put(
            runD8WithMainDexList(mode, input, Lists.reverse(mainDexClasses), TestMode.FROM_FILE),
            mode.toString() + ": main-dex list files (reversed)");
        testDirs.put(
            runD8WithMainDexList(
                mode, input, Lists.reverse(mainDexClasses), TestMode.FROM_CLASS_NAMES),
            mode.toString() + ": main-dex classes (reversed)");
      }

      byte[] ref = null;
      byte[] ref2 = null;
      for (Path testDir : testDirs.keySet()) {
        Path primaryDexFile = testDir.resolve(ToolHelper.DEFAULT_DEX_FILENAME);
        Path secondaryDexFile = testDir.resolve("classes2.dex");
        assertTrue(Files.exists(primaryDexFile));
        boolean hasSecondaryDexFile = !allClasses && mode == CompilationMode.DEBUG;
        assertEquals(hasSecondaryDexFile, Files.exists(secondaryDexFile));
        byte[] content = Files.readAllBytes(primaryDexFile);
        if (ref == null) {
          ref = content;
        } else {
          assertArrayEquals("primary: " + testDirs.get(testDir), ref, content);
        }
        if (hasSecondaryDexFile) {
          content = Files.readAllBytes(primaryDexFile);
          if (ref2 == null) {
            ref2 = content;
          } else {
            assertArrayEquals("secondary: " + testDirs.get(testDir), ref2, content);
          }
        }
      }
    }
  }

  @Test
  public void deterministicTest() throws Exception {
    // Synthesize a dex containing a few empty classes including some in the default package.
    // Everything can fit easily in a single dex file.
    ImmutableList<String> classes = new ImmutableList.Builder<String>()
        .add("A")
        .add("B")
        .add("C")
        .add("D")
        .add("E")
        .add("F")
        .add("A1")
        .add("A2")
        .add("A3")
        .add("A4")
        .add("A5")
        .add("maindexlist.A")
        .add("maindexlist.B")
        .add("maindexlist.C")
        .add("maindexlist.D")
        .add("maindexlist.E")
        .add("maindexlist.F")
        .add("maindexlist.A1")
        .add("maindexlist.A2")
        .add("maindexlist.A3")
        .add("maindexlist.A4")
        .add("maindexlist.A5")
        .build();

    JasminBuilder jasminBuilder = new JasminBuilder();
    for (String name : classes) {
      jasminBuilder.addClass(name);
    }
    Path input = temp.newFolder().toPath().resolve("input.zip");
    ToolHelper.runR8(
            ToolHelper.prepareR8CommandBuilder(jasminBuilder.build())
                .setDisableTreeShaking(true)
                .setDisableMinification(true)
                .build())
        .writeToZipForTesting(input, OutputMode.DexIndexed);

    // Test with empty main dex list.
    runDeterministicTest(input, null, true);

    // Test with main-dex list with all classes.
    runDeterministicTest(input, classes, true);

    // Test with main-dex list with first and second half of the classes.
    List<List<String>> partitions = Lists.partition(classes, classes.size() / 2);
    runDeterministicTest(input, partitions.get(0), false);
    runDeterministicTest(input, partitions.get(1), false);

    // Test with main-dex list with every second of the classes.
    runDeterministicTest(input,
        IntStream.range(0, classes.size())
            .filter(n -> n % 2 == 0)
            .mapToObj(classes::get)
            .collect(Collectors.toList()), false);
    runDeterministicTest(input,
        IntStream.range(0, classes.size())
            .filter(n -> n % 2 == 1)
            .mapToObj(classes::get)
            .collect(Collectors.toList()), false);
  }

  private static String typeToEntry(String type) {
    return type.replace(".", "/") + FileUtils.CLASS_EXTENSION;
  }

  private void failedToFindClassInExpectedFile(Path outDir, String clazz) throws IOException {
    Files.list(outDir)
        .filter(FileUtils::isDexFile)
        .forEach(
            p -> {
              try {
                CodeInspector i =
                    new CodeInspector(AndroidApp.builder().addProgramFiles(p).build());
                assertFalse("Found " + clazz + " in file " + p, i.clazz(clazz).isPresent());
              } catch (IOException e) {
                e.printStackTrace();
              }
            });
    fail("Failed to find class " + clazz + "in any file...");
  }

  private void assertMainDexClass(FoundClassSubject clazz, List<String> mainDex) {
    if (!mainDex.contains(clazz.toString())) {
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < mainDex.size(); i++) {
        builder.append(i == 0 ? "[" : ", ");
        builder.append(mainDex.get(i));
      }
      builder.append("]");
      fail("Class " + clazz + " found in main dex, " +
          "only expected explicit main dex classes " + builder + " in main dex file");
    }
  }

  private enum MultiDexTestMode {
    SINGLE_FILE,
    MULTIPLE_FILES,
    STRINGS,
    FILES_AND_STRINGS
  }

  private void doVerifyMainDexContains(
      List<String> mainDex,
      Path app,
      boolean singleDexApp,
      boolean minimalMainDex,
      MultiDexTestMode testMode,
      DiagnosticsHandler handler)
      throws IOException, CompilationFailedException {
    AndroidApp originalApp = AndroidApp.builder().addProgramFiles(app).build();
    CodeInspector originalInspector = new CodeInspector(originalApp);
    for (String clazz : mainDex) {
      assertTrue("Class " + clazz + " does not exist in input",
          originalInspector.clazz(clazz).isPresent());
    }
    Path outDir = temp.newFolder().toPath();
    R8Command.Builder builder =
        R8Command.builder(handler)
            .addProgramFiles(app)
            .setMode(
                minimalMainDex && mainDex.size() > 0
                    ? CompilationMode.DEBUG
                    : CompilationMode.RELEASE)
            .setOutput(outDir, OutputMode.DexIndexed)
            .setDisableTreeShaking(true)
            .setDisableMinification(true);

    switch (testMode) {
      case SINGLE_FILE:
        Path mainDexList = temp.newFile().toPath();
        FileUtils.writeTextFile(mainDexList, ListUtils.map(mainDex, MainDexListTests::typeToEntry));
        builder.addMainDexListFiles(mainDexList);
        break;
      case MULTIPLE_FILES: {
          // Partition the main dex list into several files.
          List<List<String>> partitions = Lists.partition(mainDex, Math.max(mainDex.size() / 3, 1));
        List<Path> mainDexListFiles = new ArrayList<>();
        for (List<String> partition : partitions) {
          Path partialMainDexList = temp.newFile().toPath();
          FileUtils.writeTextFile(partialMainDexList,
              ListUtils.map(partition, MainDexListTests::typeToEntry));
          mainDexListFiles.add(partialMainDexList);
        }
        builder.addMainDexListFiles(mainDexListFiles);
        break;
      }
      case STRINGS:
        builder.addMainDexClasses(mainDex);
        break;
      case FILES_AND_STRINGS: {
        // Partion the main dex list add some parts through files and the other parts using strings.
        List<List<String>> partitions = Lists.partition(mainDex, Math.max(mainDex.size() / 3, 1));
        List<Path> mainDexListFiles = new ArrayList<>();
        for (int i = 0; i < partitions.size(); i++) {
          List<String> partition = partitions.get(i);
          if (i % 2 == 0) {
            Path partialMainDexList = temp.newFile().toPath();
            FileUtils.writeTextFile(partialMainDexList,
                ListUtils.map(partition, MainDexListTests::typeToEntry));
            mainDexListFiles.add(partialMainDexList);
          } else {
            builder.addMainDexClasses(mainDex);
          }
        }
        builder.addMainDexListFiles(mainDexListFiles);
        break;
      }
    }

    ToolHelper.runR8(builder.build());
    if (!singleDexApp && !minimalMainDex) {
      assertTrue("Output run only produced one dex file.",
          1 < Files.list(outDir).filter(FileUtils::isDexFile).count());
    }
    CodeInspector inspector =
        new CodeInspector(
            AndroidApp.builder().addProgramFiles(outDir.resolve("classes.dex")).build());
    for (String clazz : mainDex) {
      if (!inspector.clazz(clazz).isPresent()) {
        failedToFindClassInExpectedFile(outDir, clazz);
      }
    }
    if (minimalMainDex && mainDex.size() > 0) {
      inspector.forAllClasses(clazz -> assertMainDexClass(clazz, mainDex));
    }
  }

  private void verifyMainDexContains(List<String> mainDex, Path app, boolean singleDexApp)
      throws Throwable {
    try {
      verifyMainDexContains(
          mainDex,
          app,
          singleDexApp,
          test -> {
            try {
              test.run(new TestDiagnosticMessagesImpl());
            } catch (Throwable e) {
              throw new RuntimeException(e);
            }
          });
    } catch (RuntimeException e) {
      throw e.getCause();
    }
  }

  private void verifyMainDexContains(
      List<String> mainDex, Path app, boolean singleDexApp, Consumer<Runner> runner) {
    for (MultiDexTestMode multiDexTestMode : MultiDexTestMode.values()) {
      runner.accept(
          (handler) ->
              doVerifyMainDexContains(
                  mainDex, app, singleDexApp, false, multiDexTestMode, handler));
      runner.accept(
          (handler) ->
              doVerifyMainDexContains(mainDex, app, singleDexApp, true, multiDexTestMode, handler));
    }
  }

  private static void generateApplication(Path output, List<String> classes, int methodCount)
      throws IOException {
    ArchiveConsumer consumer = new ArchiveConsumer(output);
    for (String typename : classes) {
      String descriptor = DescriptorUtils.javaTypeToDescriptor(typename);
      byte[] bytes =
          transformer(ClassStub.class)
              .setClassDescriptor(descriptor)
              .addClassTransformer(
                  new ClassTransformer() {
                    @Override
                    public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions) {
                      // This strips <init>() too.
                      if (name.equals("methodStub")) {
                        for (int i = 0; i < methodCount; i++) {
                          MethodVisitor mv =
                              super.visitMethod(
                                  access, "method" + i, descriptor, signature, exceptions);
                          mv.visitCode();
                          mv.visitInsn(Opcodes.RETURN);
                          mv.visitMaxs(0, 0);
                          mv.visitEnd();
                        }
                      }
                      return null;
                    }
                  })
              .transform();
      consumer.accept(ByteDataView.of(bytes), descriptor, null);
    }
    consumer.finished(null);
  }

  // Simple stub/template for generating the input classes.
  public static class ClassStub {
    public static void methodStub() {}
  }
}
