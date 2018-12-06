// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.ZIP_EXTENSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OffOrAuto;
import com.android.tools.r8.utils.TestDescriptionWatcher;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public abstract class RunExamplesAndroidOTest
      <B extends BaseCommand.Builder<? extends BaseCommand, B>> {
  static final String EXAMPLE_DIR = ToolHelper.EXAMPLES_ANDROID_O_BUILD_DIR;

  abstract class TestRunner<C extends TestRunner<C>> {
    final String testName;
    final String packageName;
    final String mainClass;
    final List<String> args = new ArrayList<>();

    AndroidApiLevel androidJarVersion = null;

    final List<Consumer<InternalOptions>> optionConsumers = new ArrayList<>();
    final List<Consumer<CodeInspector>> dexInspectorChecks = new ArrayList<>();
    final List<UnaryOperator<B>> builderTransformations = new ArrayList<>();

    TestRunner(String testName, String packageName, String mainClass) {
      this.testName = testName;
      this.packageName = packageName;
      this.mainClass = mainClass;
    }

    abstract C self();

    C withDexCheck(Consumer<CodeInspector> check) {
      dexInspectorChecks.add(check);
      return self();
    }

    C withClassCheck(Consumer<FoundClassSubject> check) {
      return withDexCheck(inspector -> inspector.forAllClasses(check));
    }

    C withMethodCheck(Consumer<FoundMethodSubject> check) {
      return withClassCheck(clazz -> clazz.forAllMethods(check));
    }

    <T extends InstructionSubject> C withInstructionCheck(
        Predicate<InstructionSubject> filter, Consumer<T> check) {
      return withMethodCheck(method -> {
        if (method.isAbstract()) {
          return;
        }
        Iterator<T> iterator = method.iterateInstructions(filter);
        while (iterator.hasNext()) {
          check.accept(iterator.next());
        }
      });
    }

    C withOptionConsumer(Consumer<InternalOptions> consumer) {
      optionConsumers.add(consumer);
      return self();
    }

    C withMainDexClass(String... classes) {
      return withBuilderTransformation(builder -> builder.addMainDexClasses(classes));
    }

    C withInterfaceMethodDesugaring(OffOrAuto behavior) {
      return withOptionConsumer(o -> o.interfaceMethodDesugaring = behavior);
    }

    C withTryWithResourcesDesugaring(OffOrAuto behavior) {
      return withOptionConsumer(o -> o.tryWithResourcesDesugaring = behavior);
    }

    C withBuilderTransformation(UnaryOperator<B> builderTransformation) {
      builderTransformations.add(builderTransformation);
      return self();
    }

    C withArg(String arg) {
      args.add(arg);
      return self();
    }

    void combinedOptionConsumer(InternalOptions options) {
      for (Consumer<InternalOptions> consumer : optionConsumers) {
        consumer.accept(options);
      }
    }

    Path build() throws Throwable {
      Path inputFile = getInputJar();
      Path out = temp.getRoot().toPath().resolve(testName + ZIP_EXTENSION);

      build(inputFile, out);
      return out;
    }

    Path getInputJar() {
      return Paths.get(EXAMPLE_DIR, packageName + JAR_EXTENSION);
    }

    void run() throws Throwable {
      if (minSdkErrorExpected(testName)) {
        thrown.expect(CompilationFailedException.class);
      }

      String qualifiedMainClass = packageName + "." + mainClass;
      Path inputFile = getInputJar();
      Path out = temp.getRoot().toPath().resolve(testName + ZIP_EXTENSION);

      build(inputFile, out);

      if (!ToolHelper.artSupported() && !ToolHelper.dealsWithGoldenFiles()) {
        return;
      }

      if (!dexInspectorChecks.isEmpty()) {
        CodeInspector inspector = new CodeInspector(out);
        for (Consumer<CodeInspector> check : dexInspectorChecks) {
          check.accept(inspector);
        }
      }

      execute(testName, qualifiedMainClass, new Path[]{inputFile}, new Path[]{out}, args);
    }

    abstract C withMinApiLevel(AndroidApiLevel minApiLevel);

    C withAndroidJar(AndroidApiLevel androidJarVersion) {
      assert this.androidJarVersion == null;
      this.androidJarVersion = androidJarVersion;
      return self();
    }

    void build(Path inputFile, Path out) throws Throwable {
      build(inputFile, out, OutputMode.DexIndexed);
    }

    abstract void build(Path inputFile, Path out, OutputMode mode) throws Throwable;
  }

  private static List<String> minSdkErrorExpected =
      ImmutableList.of(
          "invokepolymorphic-error-due-to-min-sdk", "invokecustom-error-due-to-min-sdk");

  private static Map<DexVm.Version, List<String>> failsOn;

  static {
    ImmutableMap.Builder<DexVm.Version, List<String>> builder = ImmutableMap.builder();
    builder
        .put(
            DexVm.Version.V4_0_4, ImmutableList.of(
                // API not supported
                "paramnames",
                "repeat_annotations_new_api",
                // Dex version not supported
                "invokepolymorphic",
                "invokecustom",
                "invokecustom2",
                "DefaultMethodInAndroidJar25",
                "StaticMethodInAndroidJar25",
                "testMissingInterfaceDesugared2AndroidO",
                "testCallToMissingSuperInterfaceDesugaredAndroidO",
                "testMissingSuperDesugaredAndroidO"
            ))
        .put(
            DexVm.Version.V4_4_4, ImmutableList.of(
                // API not supported
                "paramnames",
                "repeat_annotations_new_api",
                // Dex version not supported
                "invokepolymorphic",
                "invokecustom",
                "invokecustom2",
                "DefaultMethodInAndroidJar25",
                "StaticMethodInAndroidJar25",
                "testMissingInterfaceDesugared2AndroidO",
                "testCallToMissingSuperInterfaceDesugaredAndroidO",
                "testMissingSuperDesugaredAndroidO"
            ))
        .put(
            DexVm.Version.V5_1_1, ImmutableList.of(
                // API not supported
                "paramnames",
                "repeat_annotations_new_api",
                // Dex version not supported
                "invokepolymorphic",
                "invokecustom",
                "invokecustom2",
                "DefaultMethodInAndroidJar25",
                "StaticMethodInAndroidJar25",
                "testMissingInterfaceDesugared2AndroidO",
                "testCallToMissingSuperInterfaceDesugaredAndroidO",
                "testMissingSuperDesugaredAndroidO"
            ))
        .put(
            DexVm.Version.V6_0_1, ImmutableList.of(
                // API not supported
                "paramnames",
                "repeat_annotations_new_api",
                // Dex version not supported
                "invokepolymorphic",
                "invokecustom",
                "invokecustom2",
                "DefaultMethodInAndroidJar25",
                "StaticMethodInAndroidJar25",
                "testMissingInterfaceDesugared2AndroidO",
                "testCallToMissingSuperInterfaceDesugaredAndroidO",
                "testMissingSuperDesugaredAndroidO"
            ))
        .put(
            DexVm.Version.V7_0_0, ImmutableList.of(
                // API not supported
                "paramnames",
                // Dex version not supported
                "invokepolymorphic",
                "invokecustom",
                "invokecustom2",
                "testMissingInterfaceDesugared2AndroidO",
                "testCallToMissingSuperInterfaceDesugaredAndroidO",
                "testMissingSuperDesugaredAndroidO"
            ))
        .put(
            DexVm.Version.V9_0_0, ImmutableList.of(
                // TODO(120402963): Triage.
                "invokecustom",
                "invokecustom2"
            ))
        .put(
            DexVm.Version.DEFAULT, ImmutableList.of()
        );
    failsOn = builder.build();
  }

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Rule
  public TestDescriptionWatcher watcher = new TestDescriptionWatcher();

  boolean failsOn(Map<ToolHelper.DexVm.Version, List<String>> failsOn, String name) {
    Version vmVersion = ToolHelper.getDexVm().getVersion();
    return failsOn.containsKey(vmVersion)
        && failsOn.get(vmVersion).contains(name);
  }

  boolean expectedToFail(String name) {
    return failsOn(failsOn, name);
  }

  boolean skipRunningOnJvm(String name) {
    return name.equals("stringconcat");
  }

  boolean minSdkErrorExpected(String testName) {
    return minSdkErrorExpected.contains(testName);
  }

  @Test
  public void stringConcat() throws Throwable {
    test("stringconcat", "stringconcat", "StringConcat")
        .withMinApiLevel(AndroidApiLevel.K)
        .run();
  }

  @Test
  public void invokeCustom() throws Throwable {
    test("invokecustom", "invokecustom", "InvokeCustom")
        .withMinApiLevel(AndroidApiLevel.O)
        .run();
  }

  @Test
  public void invokeCustom2() throws Throwable {
    test("invokecustom2", "invokecustom2", "InvokeCustom")
        .withMinApiLevel(AndroidApiLevel.O)
        .run();
  }

  @Test
  public void invokeCustomErrorDueToMinSdk() throws Throwable {
    test("invokecustom-error-due-to-min-sdk", "invokecustom", "InvokeCustom")
        .withMinApiLevel(AndroidApiLevel.N_MR1)
        .run();
  }

  @Test
  public void invokePolymorphic() throws Throwable {
    test("invokepolymorphic", "invokepolymorphic", "InvokePolymorphic")
        .withMinApiLevel(AndroidApiLevel.O)
        .run();
  }

  @Test
  public void invokePolymorphicErrorDueToMinSdk() throws Throwable {
    test("invokepolymorphic-error-due-to-min-sdk", "invokepolymorphic", "InvokePolymorphic")
        .withMinApiLevel(AndroidApiLevel.N_MR1)
        .run();
  }

  @Test
  public void lambdaDesugaring() throws Throwable {
    test("lambdadesugaring", "lambdadesugaring", "LambdaDesugaring")
        .withMinApiLevel(AndroidApiLevel.K)
        .run();
  }

  @Test
  public void lambdaDesugaringNPlus() throws Throwable {
    test("lambdadesugaringnplus", "lambdadesugaringnplus", "LambdasWithStaticAndDefaultMethods")
        .withMinApiLevel(AndroidApiLevel.K)
        .withInterfaceMethodDesugaring(OffOrAuto.Auto)
        .run();
  }

  @Test
  public void desugarDefaultMethodInAndroidJar25() throws Throwable {
    test("DefaultMethodInAndroidJar25", "desugaringwithandroidjar25", "DefaultMethodInAndroidJar25")
        .withMinApiLevel(AndroidApiLevel.K)
        .withAndroidJar(AndroidApiLevel.O)
        .withInterfaceMethodDesugaring(OffOrAuto.Auto)
        .run();
  }

  @Test
  public void desugarStaticMethodInAndroidJar25() throws Throwable {
    test("StaticMethodInAndroidJar25", "desugaringwithandroidjar25", "StaticMethodInAndroidJar25")
        .withMinApiLevel(AndroidApiLevel.K)
        .withAndroidJar(AndroidApiLevel.O)
        .withInterfaceMethodDesugaring(OffOrAuto.Auto)
        .run();
  }

  @Test
  public void lambdaDesugaringValueAdjustments() throws Throwable {
    test("lambdadesugaring-value-adjustments", "lambdadesugaring", "ValueAdjustments")
        .withMinApiLevel(AndroidApiLevel.K)
        .run();
  }

  @Test
  public void paramNames() throws Throwable {
    test("paramnames", "paramnames", "ParameterNames")
        .withMinApiLevel(AndroidApiLevel.O)
        .run();
  }

  @Test
  public void repeatAnnotationsNewApi() throws Throwable {
    // No need to specify minSdk as repeat annotations are handled by javac and we do not have
    // to do anything to support them. The library methods to access them just have to be in
    // the system.
    test("repeat_annotations_new_api", "repeat_annotations", "RepeatAnnotationsNewApi");
  }

  @Test
  public void repeatAnnotations() throws Throwable {
    // No need to specify minSdk as repeat annotations are handled by javac and we do not have
    // to do anything to support them. The library methods to access them just have to be in
    // the system.
    test("repeat_annotations", "repeat_annotations", "RepeatAnnotations").run();
  }

  @Test
  public void testTryWithResources() throws Throwable {
    test("try-with-resources-simplified", "trywithresources", "TryWithResourcesNotDesugaredTests")
        .withTryWithResourcesDesugaring(OffOrAuto.Off)
        .run();
  }

  @Test
  public void testTryWithResourcesDesugared() throws Throwable {
    test("try-with-resources-simplified", "trywithresources", "TryWithResourcesDesugaredTests")
        .withTryWithResourcesDesugaring(OffOrAuto.Auto)
        .withInstructionCheck(InstructionSubject::isInvoke,
            (InvokeInstructionSubject invoke) -> {
              Assert.assertFalse(invoke.invokedMethod().name.toString().equals("addSuppressed"));
              Assert.assertFalse(invoke.invokedMethod().name.toString().equals("getSuppressed"));
            })
        .run();
  }


  @Test
  public void testLambdaDesugaringWithMainDexList1() throws Throwable {
    // Minimal case: there are synthesized classes but not form the main dex class.
    testIntermediateWithMainDexList(
        "lambdadesugaring",
        1,
        "lambdadesugaring.LambdaDesugaring$I");
  }

  @Test
  public void testLambdaDesugaringWithMainDexList2() throws Throwable {
    // Main dex class has many lambdas.
    testIntermediateWithMainDexList("lambdadesugaring",
        33,
        "lambdadesugaring.LambdaDesugaring$Refs$B");
  }

  @Test
  public void testInterfaceDesugaringWithMainDexList1() throws Throwable {
    // Main dex interface has one static method.
    testIntermediateWithMainDexList(
        "interfacemethods",
        Paths.get(ToolHelper.EXAMPLES_ANDROID_N_BUILD_DIR, "interfacemethods" + JAR_EXTENSION),
        2,
        "interfacemethods.I1");
  }


  @Test
  public void testInterfaceDesugaringWithMainDexList2() throws Throwable {
    // Main dex interface has one default method.
    testIntermediateWithMainDexList(
        "interfacemethods",
        Paths.get(ToolHelper.EXAMPLES_ANDROID_N_BUILD_DIR, "interfacemethods" + JAR_EXTENSION),
        2,
        "interfacemethods.I2");
  }

  @Test
  public void testInterfaceDispatchClasses() throws Throwable {
    test("interfacedispatchclasses", "interfacedispatchclasses", "TestInterfaceDispatchClasses")
        .withMinApiLevel(AndroidApiLevel.K) // K to create dispatch classes
        .withAndroidJar(AndroidApiLevel.O)
        .withArg(String.valueOf(ToolHelper.getMinApiLevelForDexVm().getLevel() >= 24))
        .run();
  }

  private void testIntermediateWithMainDexList(
      String packageName,
      int expectedMainDexListSize,
      String... mainDexClasses)
      throws Throwable {
    testIntermediateWithMainDexList(
        packageName,
        Paths.get(EXAMPLE_DIR, packageName + JAR_EXTENSION),
        expectedMainDexListSize,
        mainDexClasses);
  }

  protected void testIntermediateWithMainDexList(
      String packageName,
      Path input,
      int expectedMainDexListSize,
      String... mainDexClasses)
      throws Throwable {
    AndroidApiLevel minApi = AndroidApiLevel.K;

    // Full build, will be used as reference.
    TestRunner<?> full =
        test(packageName + "full", packageName, "N/A")
            .withInterfaceMethodDesugaring(OffOrAuto.Auto)
            .withMinApiLevel(minApi)
            .withOptionConsumer(option -> option.minimalMainDex = true)
            .withOptionConsumer(option -> option.enableInheritanceClassInDexDistributor = false)
            .withMainDexClass(mainDexClasses);
    Path fullDexes = temp.getRoot().toPath().resolve(packageName + "full" + ZIP_EXTENSION);
    full.build(input, fullDexes);

    // Builds with intermediate in both output mode.
    Path dexesThroughIndexedIntermediate = buildDexThroughIntermediate(
        packageName, input, OutputMode.DexIndexed, minApi, mainDexClasses);
    Path dexesThroughFilePerInputClassIntermediate = buildDexThroughIntermediate(
        packageName, input, OutputMode.DexFilePerClassFile, minApi, mainDexClasses);

    // Collect main dex types.
    CodeInspector fullInspector = getMainDexInspector(fullDexes);
    CodeInspector indexedIntermediateInspector =
        getMainDexInspector(dexesThroughIndexedIntermediate);
    CodeInspector filePerInputClassIntermediateInspector =
        getMainDexInspector(dexesThroughFilePerInputClassIntermediate);
    Collection<String> fullMainClasses = new HashSet<>();
    fullInspector.forAllClasses(
        clazz -> fullMainClasses.add(clazz.getFinalDescriptor()));
    Collection<String> indexedIntermediateMainClasses = new HashSet<>();
    indexedIntermediateInspector.forAllClasses(
        clazz -> indexedIntermediateMainClasses.add(clazz.getFinalDescriptor()));
    Collection<String> filePerInputClassIntermediateMainClasses = new HashSet<>();
    filePerInputClassIntermediateInspector.forAllClasses(
        clazz -> filePerInputClassIntermediateMainClasses.add(clazz.getFinalDescriptor()));

    // Check.
    Assert.assertEquals(expectedMainDexListSize, fullMainClasses.size());
    Assert.assertEquals(fullMainClasses, indexedIntermediateMainClasses);
    Assert.assertEquals(fullMainClasses, filePerInputClassIntermediateMainClasses);
  }

  protected Path buildDexThroughIntermediate(
      String packageName,
      Path input,
      OutputMode outputMode,
      AndroidApiLevel minApi,
      String... mainDexClasses)
      throws Throwable {
    Path intermediateDex =
        temp.getRoot().toPath().resolve(packageName + "intermediate" + ZIP_EXTENSION);
    // Build intermediate with D8.
    D8Command.Builder command = D8Command.builder()
        .setOutput(intermediateDex, outputMode)
        .setMinApiLevel(minApi.getLevel())
        .addLibraryFiles(ToolHelper.getAndroidJar(minApi))
        .setIntermediate(true)
        .addProgramFiles(input);
    ToolHelper.runD8(command, option -> {
      option.interfaceMethodDesugaring = OffOrAuto.Auto;
    });

    TestRunner<?> end =
        test(packageName + "dex", packageName, "N/A")
            .withOptionConsumer(option -> option.minimalMainDex = true)
            .withOptionConsumer(option -> option.enableInheritanceClassInDexDistributor = false)
            .withMainDexClass(mainDexClasses)
            .withMinApiLevel(minApi);

    Path dexesThroughIntermediate =
        temp.getRoot().toPath().resolve(packageName + "dex" + ZIP_EXTENSION);
    end.build(intermediateDex, dexesThroughIntermediate);
    return dexesThroughIntermediate;
  }

  abstract RunExamplesAndroidOTest<B>.TestRunner<?> test(String testName, String packageName,
      String mainClass);

  void execute(String testName,
      String qualifiedMainClass, Path[] jars, Path[] dexes) throws IOException {
    execute(testName, qualifiedMainClass, jars, dexes, Collections.emptyList());
  }

  void execute(String testName,
      String qualifiedMainClass, Path[] jars, Path[] dexes, List<String> args) throws IOException {

    boolean expectedToFail = expectedToFail(testName);
    try {
      String output = ToolHelper.runArtNoVerificationErrors(
          Arrays.stream(dexes).map(path -> path.toString()).collect(Collectors.toList()),
          qualifiedMainClass,
          builder -> {
            for (String arg : args) {
              builder.appendProgramArgument(arg);
            }
          });
      if (!expectedToFail
          && !skipRunningOnJvm(testName)
          && !ToolHelper.compareAgaintsGoldenFiles()) {
        ArrayList<String> javaArgs = Lists.newArrayList(args);
        javaArgs.add(0, qualifiedMainClass);
        ToolHelper.ProcessResult javaResult =
            ToolHelper.runJava(ImmutableList.copyOf(jars), javaArgs.toArray(new String[0]));
        assertEquals("JVM run failed", javaResult.exitCode, 0);
        assertTrue(
            "JVM output does not match art output.\n\tjvm: "
                + javaResult.stdout
                + "\n\tart: "
                + output,
            output.replace("\r", "").equals(javaResult.stdout.replace("\r", "")));
      }
    } catch (Throwable t) {
      if (ToolHelper.isWindows()) {
        System.out.println("Throwable: " + t.getMessage());
        t.printStackTrace(System.out);
        System.out.println("expectedToFail: " + expectedToFail);
        System.out.println(
            "ToolHelper.compareAgaintsGoldenFiles(): " + ToolHelper.compareAgaintsGoldenFiles());
      }
      assert expectedToFail && !ToolHelper.compareAgaintsGoldenFiles();
    }
  }

  protected CodeInspector getMainDexInspector(Path zip)
      throws ZipException, IOException, ExecutionException {
    try (ZipFile zipFile = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
      try (InputStream in =
          zipFile.getInputStream(zipFile.getEntry(ToolHelper.DEFAULT_DEX_FILENAME))) {
        return new CodeInspector(
            AndroidApp.builder()
                .addDexProgramData(ByteStreams.toByteArray(in), Origin.unknown())
                .build());
      }
    }
  }

}
