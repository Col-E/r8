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
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.FoundClassSubject;
import com.android.tools.r8.utils.DexInspector.FoundMethodSubject;
import com.android.tools.r8.utils.DexInspector.InstructionSubject;
import com.android.tools.r8.utils.DexInspector.InvokeInstructionSubject;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OffOrAuto;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
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

    Integer androidJarVersion = null;

    final List<Consumer<InternalOptions>> optionConsumers = new ArrayList<>();
    final List<Consumer<DexInspector>> dexInspectorChecks = new ArrayList<>();
    final List<UnaryOperator<B>> builderTransformations = new ArrayList<>();

    TestRunner(String testName, String packageName, String mainClass) {
      this.testName = testName;
      this.packageName = packageName;
      this.mainClass = mainClass;
    }

    abstract C self();

    C withDexCheck(Consumer<DexInspector> check) {
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
        thrown.expect(ApiLevelException.class);
      }

      String qualifiedMainClass = packageName + "." + mainClass;
      Path inputFile = getInputJar();
      Path out = temp.getRoot().toPath().resolve(testName + ZIP_EXTENSION);

      build(inputFile, out);

      if (!ToolHelper.artSupported()) {
        return;
      }

      if (!dexInspectorChecks.isEmpty()) {
        DexInspector inspector = new DexInspector(out);
        for (Consumer<DexInspector> check : dexInspectorChecks) {
          check.accept(inspector);
        }
      }

      execute(testName, qualifiedMainClass, new Path[]{inputFile}, new Path[]{out});
    }

    abstract C withMinApiLevel(int minApiLevel);

    C withAndroidJar(int androidJarVersion) {
      assert this.androidJarVersion == null;
      this.androidJarVersion = androidJarVersion;
      return self();
    }

    abstract void build(Path inputFile, Path out) throws Throwable;
  }

  private static List<String> minSdkErrorExpected =
      ImmutableList.of(
          "invokepolymorphic-error-due-to-min-sdk", "invokecustom-error-due-to-min-sdk");

  private static Map<DexVm.Version, List<String>> failsOn =
      ImmutableMap.of(
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
          ),
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
          ),
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
          ),
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
          ),
          DexVm.Version.DEFAULT, ImmutableList.of()
      );

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

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
        .withMinApiLevel(AndroidApiLevel.K.getLevel())
        .run();
  }

  @Test
  public void invokeCustom() throws Throwable {
    test("invokecustom", "invokecustom", "InvokeCustom")
        .withMinApiLevel(AndroidApiLevel.O.getLevel())
        .run();
  }

  @Test
  public void invokeCustom2() throws Throwable {
    test("invokecustom2", "invokecustom2", "InvokeCustom")
        .withMinApiLevel(AndroidApiLevel.O.getLevel())
        .run();
  }

  @Test
  public void invokeCustomErrorDueToMinSdk() throws Throwable {
    test("invokecustom-error-due-to-min-sdk", "invokecustom", "InvokeCustom")
        .withMinApiLevel(25)
        .run();
  }

  @Test
  public void invokePolymorphic() throws Throwable {
    test("invokepolymorphic", "invokepolymorphic", "InvokePolymorphic")
        .withMinApiLevel(AndroidApiLevel.O.getLevel())
        .run();
  }

  @Test
  public void invokePolymorphicErrorDueToMinSdk() throws Throwable {
    test("invokepolymorphic-error-due-to-min-sdk", "invokepolymorphic", "InvokePolymorphic")
        .withMinApiLevel(25)
        .run();
  }

  @Test
  public void lambdaDesugaring() throws Throwable {
    test("lambdadesugaring", "lambdadesugaring", "LambdaDesugaring")
        .withMinApiLevel(AndroidApiLevel.K.getLevel())
        .run();
  }

  @Test
  public void lambdaDesugaringNPlus() throws Throwable {
    test("lambdadesugaringnplus", "lambdadesugaringnplus", "LambdasWithStaticAndDefaultMethods")
        .withMinApiLevel(AndroidApiLevel.K.getLevel())
        .withInterfaceMethodDesugaring(OffOrAuto.Auto)
        .run();
  }

  @Test
  public void desugarDefaultMethodInAndroidJar25() throws Throwable {
    test("DefaultMethodInAndroidJar25", "desugaringwithandroidjar25", "DefaultMethodInAndroidJar25")
        .withMinApiLevel(AndroidApiLevel.K.getLevel())
        .withAndroidJar(AndroidApiLevel.O.getLevel())
        .withInterfaceMethodDesugaring(OffOrAuto.Auto)
        .run();
  }

  @Test
  public void desugarStaticMethodInAndroidJar25() throws Throwable {
    test("StaticMethodInAndroidJar25", "desugaringwithandroidjar25", "StaticMethodInAndroidJar25")
        .withMinApiLevel(AndroidApiLevel.K.getLevel())
        .withAndroidJar(AndroidApiLevel.O.getLevel())
        .withInterfaceMethodDesugaring(OffOrAuto.Auto)
        .run();
  }

  @Test
  public void lambdaDesugaringValueAdjustments() throws Throwable {
    test("lambdadesugaring-value-adjustments", "lambdadesugaring", "ValueAdjustments")
        .withMinApiLevel(AndroidApiLevel.K.getLevel())
        .run();
  }

  @Test
  public void paramNames() throws Throwable {
    test("paramnames", "paramnames", "ParameterNames")
        .withMinApiLevel(AndroidApiLevel.O.getLevel())
        .run();
  }

  @Test
  public void repeatAnnotationsNewApi() throws Throwable {
    // No need to specify minSdk as repeat annotations are handled by javac and we do not have
    // to do anything to support them. The library methods to access them just have to be in
    // the system.
    test("repeat_annotations_new_api", "repeat_annotations", "RepeatAnnotationsNewApi").run();
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

  abstract RunExamplesAndroidOTest<B>.TestRunner<?> test(String testName, String packageName, String mainClass);

  void execute(
      String testName,
      String qualifiedMainClass, Path[] jars, Path[] dexes)
      throws IOException {

    boolean expectedToFail = expectedToFail(testName);
    if (expectedToFail) {
      thrown.expect(Throwable.class);
    }
    String output = ToolHelper.runArtNoVerificationErrors(
        Arrays.stream(dexes).map(path -> path.toString()).collect(Collectors.toList()),
        qualifiedMainClass,
        null);
    if (!expectedToFail && !skipRunningOnJvm(testName)) {
      ToolHelper.ProcessResult javaResult =
          ToolHelper.runJava(
              Arrays.stream(jars).map(path -> path.toString()).collect(Collectors.toList()),
              qualifiedMainClass);
      assertEquals("JVM run failed", javaResult.exitCode, 0);
      assertTrue(
          "JVM output does not match art output.\n\tjvm: "
              + javaResult.stdout
              + "\n\tart: "
              + output,
          output.replace("\r", "").equals(javaResult.stdout.replace("\r", "")));
    }
  }

  protected DexInspector getMainDexInspector(Path zip)
      throws ZipException, IOException, ExecutionException {
    try (ZipFile zipFile = new ZipFile(zip.toFile())) {
      try (InputStream in =
          zipFile.getInputStream(zipFile.getEntry(ToolHelper.DEFAULT_DEX_FILENAME))) {
        return new DexInspector(AndroidApp.fromDexProgramData(ByteStreams.toByteArray(in)));
      }
    }
  }

}
