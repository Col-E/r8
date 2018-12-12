// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.resolution.SingleTargetLookupTest.appInfo;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dexsplitter.DexSplitter;
import com.android.tools.r8.dexsplitter.DexSplitter.Options;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.SourceCode;
import com.android.tools.r8.ir.regalloc.LinearScanRegisterAllocator;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.android.tools.r8.ir.synthetic.SynthesizedCode;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.AbortException;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.MainDexList;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class MainDexListTests extends TestBase {

  private static final int MAX_METHOD_COUNT = Constants.U16BIT_MAX;

  private static final List<String> TWO_LARGE_CLASSES = ImmutableList.of("A", "B");
  private static final int MANY_CLASSES_COUNT = 10000;
  private static final int MANY_CLASSES_SINGLE_DEX_METHODS_PER_CLASS = 2;
  private static final int MANY_CLASSES_MULTI_DEX_METHODS_PER_CLASS = 10;
  private static List<String> MANY_CLASSES;

  interface Runner {
    void run(DiagnosticsHandler handler) throws Throwable;
  }

  @ClassRule
  public static TemporaryFolder generatedApplicationsFolder =
      ToolHelper.getTemporaryFolderForTest();

  // Generate the test applications in a @BeforeClass method, as they are used by several tests.
  @BeforeClass
  public static void generateTestApplications() throws Throwable {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (int i = 0; i < MANY_CLASSES_COUNT; ++i) {
      String pkg = i % 2 == 0 ? "a" : "b";
      builder.add(pkg + ".Class" + i);
    }
    MANY_CLASSES = builder.build();

    // Generates an application with many classes, every even in one package and every odd in
    // another. Keep the number of methods low enough for single dex application.
    AndroidApp generated = generateApplication(
        MANY_CLASSES, AndroidApiLevel.getDefault().getLevel(),
        MANY_CLASSES_SINGLE_DEX_METHODS_PER_CLASS);
    generated.write(getManyClassesSingleDexAppPath(), OutputMode.DexIndexed);

    // Generates an application with many classes, every even in one package and every odd in
    // another. Add enough methods so the application cannot fit into one dex file.
    generated = generateApplication(
        MANY_CLASSES, AndroidApiLevel.L.getLevel(), MANY_CLASSES_MULTI_DEX_METHODS_PER_CLASS);
    generated.write(getManyClassesMultiDexAppPath(), OutputMode.DexIndexed);

    // Generates an application with two classes, each with the maximum possible number of methods.
    generated = generateApplication(TWO_LARGE_CLASSES, AndroidApiLevel.N.getLevel(),
        MAX_METHOD_COUNT);
    generated.write(getTwoLargeClassesAppPath(), OutputMode.DexIndexed);
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

  private static Path getManyClassesForceMultiDexAppPath() {
    return generatedApplicationsFolder.getRoot().toPath().resolve("many-classes-stereo-forced.zip");
  }

  private static Set<DexType> parse(Path path, DexItemFactory itemFactory) throws IOException {
    return MainDexList.parseList(StringResource.fromFile(path), itemFactory);
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

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
          TestDiagnosticsHandler handler = new TestDiagnosticsHandler();
          try {
            test.run(handler);
            fail("Expect to fail, for there are too many classes for the main-dex list.");
          } catch (Throwable e) {
            assert e instanceof CompilationFailedException;
            assertEquals(1, handler.errors.size());
            DexFileOverflowDiagnostic overflow = (DexFileOverflowDiagnostic) handler.errors.get(0);
            // Make sure {@link MonoDexDistributor} was _not_ used, i.e., a spec was given.
            assertTrue(overflow.hasMainDexSpecification());
            // Make sure what exceeds the limit is the number of methods.
            assertTrue(overflow.getNumberOfMethods() > overflow.getMaximumNumberOfMethods());
            assertEquals(
                TWO_LARGE_CLASSES.size() * MAX_METHOD_COUNT, overflow.getNumberOfMethods());
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
  public void everyThirdClassInMainWithDexSplitter() throws Throwable {
    List<String> featureMappings = new ArrayList<>();

    ImmutableList.Builder<String> mainDexBuilder = ImmutableList.builder();
    for (int i = 0; i < MANY_CLASSES.size(); i++) {
      String clazz = MANY_CLASSES.get(i);
      // Write the first 2 classes into the split.
      if (i < 2) {
        featureMappings.add(clazz + ":feature1");
        continue;
      }
      if (i % 3 == 0) {
        mainDexBuilder.add(clazz);
      }
    }
    Path featureSplitMapping = temp.getRoot().toPath().resolve("splitmapping");
    Path mainDexFile = temp.getRoot().toPath().resolve("maindex");
    FileUtils.writeTextFile(featureSplitMapping, featureMappings);
    List<String> mainDexList = mainDexBuilder.build();
    FileUtils.writeTextFile(mainDexFile, ListUtils.map(mainDexList, MainDexListTests::typeToEntry));
    Path output = temp.getRoot().toPath().resolve("split_output");
    Files.createDirectories(output);

    Options options = new Options();
    options.addInputArchive(getManyClassesMultiDexAppPath().toString());
    options.setFeatureSplitMapping(featureSplitMapping.toString());
    options.setOutput(output.toString());
    options.setMainDexList(mainDexFile.toString());
    DexSplitter.run(options);
    Path baseDir = output.resolve("base");
    CodeInspector inspector =
        new CodeInspector(
            AndroidApp.builder().addProgramFiles(baseDir.resolve("classes.dex")).build());
    for (String clazz : mainDexList) {
      if (!inspector.clazz(clazz).isPresent()) {
        failedToFindClassInExpectedFile(baseDir, clazz);
      }
    }
  }

  @Test
  public void singleClassInMainDex() throws Throwable {
    ImmutableList<String> mainDex = ImmutableList.of(MANY_CLASSES.get(0));
    verifyMainDexContains(mainDex, getManyClassesSingleDexAppPath(), true);
    verifyMainDexContains(mainDex, getManyClassesMultiDexAppPath(), false);
  }

  @Test
  public void allClassesInMainDex() throws Throwable {
    // Degenerated case with an app thats fit into a single dex, and where the main dex list
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
          TestDiagnosticsHandler handler = new TestDiagnosticsHandler();
          try {
            test.run(handler);
            fail("Expect to fail, for there are too many classes for the main-dex list.");
          } catch (Throwable e) {
            assert e instanceof CompilationFailedException;
            assertEquals(1, handler.errors.size());
            DexFileOverflowDiagnostic overflow = (DexFileOverflowDiagnostic) handler.errors.get(0);
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
  public void singleEntryNoNewLine() throws Exception {
    DexItemFactory factory = new DexItemFactory();
    Set<DexType> types = MainDexList.parseList(
        StringResource.fromString("desugaringwithmissingclasstest1/Main.class", Origin.unknown()),
        factory);
    assertEquals(1, types.size());
    assertEquals(
        "Ldesugaringwithmissingclasstest1/Main;",
        types.iterator().next().toDescriptorString());
  }

  @Test
  public void validEntries() throws IOException {
    List<String> list = ImmutableList.of(
        "A.class",
        "a/b/c/D.class",
        "a/b/c/D$E.class"
    );
    DexItemFactory factory = new DexItemFactory();
    Path mainDexList = temp.getRoot().toPath().resolve("valid.txt");
    FileUtils.writeTextFile(mainDexList, list);
    Set<DexType> types = parse(mainDexList, factory);
    for (String entry : list) {
      DexType type = factory.createType("L" + entry.replace(".class", "") + ";");
      assertTrue(types.contains(type));
      assertSame(type, MainDexList.parseEntry(entry, factory));
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

  private Path runD8WithMainDexList(
      CompilationMode mode, Path input, List<String> mainDexClasses, boolean useFile)
      throws Exception {
    Path testDir = temp.newFolder().toPath();
    Path listFile = testDir.resolve("main-dex-list.txt");
    if (mainDexClasses != null && useFile) {
      FileUtils.writeTextFile(
          listFile,
          mainDexClasses
              .stream()
              .map(clazz -> clazz.replace('.', '/') + ".class")
              .collect(Collectors.toList()));
    }

    D8Command.Builder builder =
        D8Command.builder()
            .addProgramFiles(input)
            .setMode(mode)
            .setOutput(testDir, OutputMode.DexIndexed);
    if (mainDexClasses != null) {
      if (useFile) {
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
            runD8WithMainDexList(mode, input, null, true),
            mode.toString() + ": without a main-dex list");
      }
      testDirs.put(
          runD8WithMainDexList(mode, input, mainDexClasses, true),
          mode.toString() + ": main-dex list files");
      testDirs.put(
          runD8WithMainDexList(mode, input, mainDexClasses, false),
          mode.toString() + ": main-dex classes");
      if (mainDexClasses != null) {
        testDirs.put(
            runD8WithMainDexList(mode, input, Lists.reverse(mainDexClasses), true),
            mode.toString() + ": main-dex list files (reversed)");
        testDirs.put(
            runD8WithMainDexList(mode, input, Lists.reverse(mainDexClasses), false),
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
    ToolHelper.runR8(jasminBuilder.build()).writeToZip(input, OutputMode.DexIndexed);

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

  @Test
  public void checkIntermediateMultiDex() throws Exception {
    // Generates an application with many classes, every even in one package and every odd in
    // another. Add enough methods so the application cannot fit into one dex file.
    // Notice that this one allows multidex while using lower API.
    AndroidApp generated = generateApplication(
        MANY_CLASSES, AndroidApiLevel.K.getLevel(), true, MANY_CLASSES_MULTI_DEX_METHODS_PER_CLASS);
    generated.write(getManyClassesForceMultiDexAppPath(), OutputMode.DexIndexed);
    // Make sure the generated app indeed has multiple dex files.
    assertTrue(generated.getDexProgramResourcesForTesting().size() > 1);
  }

  @Test
  public void testMultiDexFailDueToMinApi() throws Exception {
    // Generates an application with many classes, every even in one package and every odd in
    // another. Add enough methods so the application cannot fit into one dex file.
    // Notice that this one fails due to the min API.
    TestDiagnosticsHandler handler = new TestDiagnosticsHandler();
    try {
      generateApplication(
          MANY_CLASSES,
          AndroidApiLevel.K.getLevel(),
          false,
          MANY_CLASSES_MULTI_DEX_METHODS_PER_CLASS,
          handler);
      fail("Expect to fail, for there are many classes while multidex is not enabled.");
    } catch (AbortException e) {
      assertEquals(1, handler.errors.size());
      DexFileOverflowDiagnostic overflow = (DexFileOverflowDiagnostic) handler.errors.get(0);
      // Make sure {@link MonoDexDistributor} was used, i.e., no main-dex specification was given.
      assertFalse(overflow.hasMainDexSpecification());
      // Make sure what exceeds the limit is the number of methods.
      assertEquals(
          MANY_CLASSES_COUNT * MANY_CLASSES_MULTI_DEX_METHODS_PER_CLASS,
          overflow.getNumberOfMethods());
    }
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
                CodeInspector i = new CodeInspector(AndroidApp.builder().addProgramFiles(p).build());
                assertFalse("Found " + clazz + " in file " + p, i.clazz(clazz).isPresent());
              } catch (IOException | ExecutionException e) {
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
      throws IOException, ExecutionException, CompilationFailedException {
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
        // Partion the main dex list into several files.
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
              test.run(new TestDiagnosticsHandler());
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

  public static AndroidApp generateApplication(List<String> classes, int minApi, int methodCount)
      throws IOException, ExecutionException {
    return generateApplication(classes, minApi, false, methodCount);
  }

  private static AndroidApp generateApplication(
      List<String> classes, int minApi, boolean intermediate, int methodCount)
      throws IOException, ExecutionException {
    return generateApplication(
        classes, minApi, intermediate, methodCount, new DiagnosticsHandler() {});
  }

  private static AndroidApp generateApplication(
      List<String> classes,
      int minApi,
      boolean intermediate,
      int methodCount,
      DiagnosticsHandler diagnosticsHandler)
      throws IOException, ExecutionException {
    Timing timing = new Timing("MainDexListTests");
    InternalOptions options =
        new InternalOptions(new DexItemFactory(), new Reporter(diagnosticsHandler));
    options.minApiLevel = minApi;
    options.intermediate = intermediate;
    DexItemFactory factory = options.itemFactory;
    AppInfo appInfo = new AppInfo(DexApplication.builder(factory, timing).build());
    DexApplication.Builder builder = DexApplication.builder(factory, timing);
    for (String clazz : classes) {
      DexString desc = factory.createString(DescriptorUtils.javaTypeToDescriptor(clazz));
      DexType type = factory.createType(desc);
      DexEncodedMethod[] directMethods = new DexEncodedMethod[methodCount];
      for (int i = 0; i < methodCount; i++) {
        MethodAccessFlags access = MethodAccessFlags.fromSharedAccessFlags(0, false);
        access.setPublic();
        access.setStatic();
        DexMethod voidReturnMethod =
            factory.createMethod(
                desc,
                factory.createString("method" + i),
                factory.voidDescriptor,
                DexString.EMPTY_ARRAY);
        Code code =
            new SynthesizedCode(
                callerPosition -> new ReturnVoidCode(voidReturnMethod, callerPosition));
        DexEncodedMethod method =
            new DexEncodedMethod(
                voidReturnMethod,
                access,
                DexAnnotationSet.empty(),
                ParameterAnnotationsList.empty(),
                code);
        IRCode ir =
            code.buildIR(method, null, GraphLense.getIdentityLense(), options, Origin.unknown());
        RegisterAllocator allocator = new LinearScanRegisterAllocator(appInfo, ir, options);
        method.setCode(ir, allocator, options);
        directMethods[i] = method;
      }
      builder.addProgramClass(
          new DexProgramClass(
              type,
              null,
              new SynthesizedOrigin("test", MainDexListTests.class),
              ClassAccessFlags.fromSharedAccessFlags(0),
              factory.objectType,
              DexTypeList.empty(),
              null,
              null,
              Collections.emptyList(),
              DexAnnotationSet.empty(),
              DexEncodedField.EMPTY_ARRAY,
              DexEncodedField.EMPTY_ARRAY,
              directMethods,
              DexEncodedMethod.EMPTY_ARRAY,
              false));
    }
    DirectMappedDexApplication application = builder.build().toDirect();
    ApplicationWriter writer =
        new ApplicationWriter(
            application,
            options,
            null,
            null,
            GraphLense.getIdentityLense(),
            NamingLens.getIdentityLens(),
            null,
            null);
    ExecutorService executor = ThreadUtils.getExecutorService(options);
    AndroidAppConsumers compatSink = new AndroidAppConsumers(options);
    try {
      writer.write(executor);
    } finally {
      executor.shutdown();
    }
    options.signalFinishedToConsumers();
    return compatSink.build();
  }

  // Code stub to generate methods with "return-void" bodies.
  private static class ReturnVoidCode implements SourceCode {

    private final Position position;

    public ReturnVoidCode(DexMethod method, Position callerPosition) {
      this.position = Position.synthetic(0, method, callerPosition);
    }

    @Override
    public int instructionCount() {
      return 1;
    }

    @Override
    public int instructionIndex(int instructionOffset) {
      return instructionOffset;
    }

    @Override
    public int instructionOffset(int instructionIndex) {
      return instructionIndex;
    }

    @Override
    public DebugLocalInfo getIncomingLocalAtBlock(int register, int blockOffset) {
      return null;
    }

    @Override
    public DebugLocalInfo getIncomingLocal(int register) {
      return null;
    }

    @Override
    public DebugLocalInfo getOutgoingLocal(int register) {
      return null;
    }

    @Override
    public int traceInstruction(int instructionIndex, IRBuilder builder) {
      return instructionIndex;
    }

    @Override
    public void setUp() {
      // Intentionally empty.
    }

    @Override
    public void clear() {
      // Intentionally empty.
    }

    @Override
    public void buildPrelude(IRBuilder builder) {
      // Intentionally empty.
    }

    @Override
    public void buildInstruction(
        IRBuilder builder, int instructionIndex, boolean firstBlockInstruction) {
      assert instructionIndex == 0;
      builder.addReturn();
    }

    @Override
    public void buildBlockTransfer(
        IRBuilder builder, int predecessorOffset, int successorOffset, boolean isExceptional) {
      throw new Unreachable();
    }

    @Override
    public void buildPostlude(IRBuilder builder) {
      // Intentionally empty.
    }

    @Override
    public void resolveAndBuildSwitch(
        int value, int fallthroughOffset, int payloadOffset, IRBuilder builder) {
      throw new Unreachable();
    }

    @Override
    public void resolveAndBuildNewArrayFilledData(
        int arrayRef, int payloadOffset, IRBuilder builder) {
      throw new Unreachable();
    }

    @Override
    public CatchHandlers<Integer> getCurrentCatchHandlers() {
      return null;
    }

    @Override
    public int getMoveExceptionRegister(int instructionIndex) {
      throw new Unreachable();
    }

    @Override
    public Position getCanonicalDebugPositionAtOffset(int offset) {
      throw new Unreachable();
    }

    @Override
    public Position getCurrentPosition() {
      return position;
    }

    @Override
    public boolean verifyRegister(int register) {
      throw new Unreachable();
    }

    @Override
    public boolean verifyCurrentInstructionCanThrow() {
      throw new Unreachable();
    }

    @Override
    public boolean verifyLocalInScope(DebugLocalInfo local) {
      throw new Unreachable();
    }
  }

  private class TestDiagnosticsHandler implements DiagnosticsHandler {

    public List<Diagnostic> errors = new ArrayList<>();

    @Override
    public void error(Diagnostic error) {
      errors.add(error);
    }
  }
}
