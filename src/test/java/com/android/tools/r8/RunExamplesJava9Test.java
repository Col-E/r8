// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.ZIP_EXTENSION;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ir.desugar.InterfaceMethodRewriter;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OffOrAuto;
import com.android.tools.r8.utils.TestDescriptionWatcher;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public abstract class RunExamplesJava9Test
    <B extends BaseCommand.Builder<? extends BaseCommand, B>> {
  static final String EXAMPLE_DIR = ToolHelper.EXAMPLES_JAVA9_BUILD_DIR;

  abstract class TestRunner<C extends TestRunner<C>> {
    final String testName;
    final String packageName;
    final String mainClass;
    final List<String> args = new ArrayList<>();

    Integer androidJarVersion = null;

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

    C withArg(String arg) {
      args.add(arg);
      return self();
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

    C withInterfaceMethodDesugaring(OffOrAuto behavior) {
      return withOptionConsumer(o -> o.interfaceMethodDesugaring = behavior);
    }

    C withTryWithResourcesDesugaring(OffOrAuto behavior) {
      return withOptionConsumer(o -> o.tryWithResourcesDesugaring = behavior);
    }

    void combinedOptionConsumer(InternalOptions options) {
      for (Consumer<InternalOptions> consumer : optionConsumers) {
        consumer.accept(options);
      }
    }

    C withBuilderTransformation(UnaryOperator<B> builderTransformation) {
      builderTransformations.add(builderTransformation);
      return self();
    }

    C withMainDexClass(String... classes) {
      return withBuilderTransformation(builder -> builder.addMainDexClasses(classes));
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

    abstract C withMinApiLevel(int minApiLevel);

    C withAndroidJar(int androidJarVersion) {
      assert this.androidJarVersion == null;
      this.androidJarVersion = androidJarVersion;
      return self();
    }

    abstract void build(Path inputFile, Path out) throws Throwable;
  }

  private static List<String> minSdkErrorExpected =
      ImmutableList.of("varhandle-error-due-to-min-sdk");

  private static Map<DexVm.Version, List<String>> failsOn;

  static {
    ImmutableMap.Builder<DexVm.Version, List<String>> builder = ImmutableMap.builder();
    builder
        .put(DexVm.Version.V4_0_4, ImmutableList.of(
            "native-private-interface-methods", // Dex version not supported
            "varhandle"
        ))
        .put(DexVm.Version.V4_4_4, ImmutableList.of(
            "native-private-interface-methods", // Dex version not supported
            "varhandle"
        ))
        .put(DexVm.Version.V5_1_1, ImmutableList.of(
            "native-private-interface-methods", // Dex version not supported
            "varhandle"
        ))
        .put(DexVm.Version.V6_0_1, ImmutableList.of(
            "native-private-interface-methods", // Dex version not supported
            "varhandle"
        ))
        .put(DexVm.Version.V7_0_0, ImmutableList.of(
            // Dex version not supported
            "varhandle"
        ))
        .put(DexVm.Version.DEFAULT, ImmutableList.of(
            // TODO(mikaelpeltier): Update runtime when the support will be ready
            "varhandle"
        ));
    failsOn = builder.build();
  }

  // Defines methods failing on JVM, specifies the output to be used for comparison.
  private static Map<String, String> expectedJvmResult =
      ImmutableMap.of(
          "twr-close-resource",
          "A\nE\nG\nH\nI\nJ\nK\n"
              + "iA\niE\niG\niH\niI\niJ\niK\n"
              + "1\n2\n3\n4\n5\n6\n7\n8\n99\n"
              + "i1\ni2\ni3\ni4\ni5\ni6\ni7\ni8\ni99\n",
          "native-private-interface-methods",
          "0: s>i>a\n"
              + "1: d>i>s>i>a\n"
              + "2: l>i>s>i>a\n"
              + "3: x>s\n"
              + "4: c>d>i>s>i>a\n",
          "desugared-private-interface-methods",
          "0: s>i>a\n"
              + "1: d>i>s>i>a\n"
              + "2: l>i>s>i>a\n"
              + "3: x>s\n"
              + "4: c>d>i>s>i>a\n"
      );

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Rule
  public TestDescriptionWatcher watcher = new TestDescriptionWatcher();

  boolean failsOn(Map<DexVm.Version, List<String>> failsOn, String name) {
    DexVm.Version vmVersion = ToolHelper.getDexVm().getVersion();
    return failsOn.containsKey(vmVersion)
        && failsOn.get(vmVersion).contains(name);
  }

  boolean expectedToFail(String name) {
    return failsOn(failsOn, name);
  }

  boolean minSdkErrorExpected(String testName) {
    return minSdkErrorExpected.contains(testName);
  }

  @Test
  public void nativePrivateInterfaceMethods() throws Throwable {
    test("native-private-interface-methods",
        "privateinterfacemethods", "PrivateInterfaceMethods")
        .withMinApiLevel(AndroidApiLevel.N.getLevel())
        .run();
  }

  @Test
  public void desugaredPrivateInterfaceMethods() throws Throwable {
    final String iName = "privateinterfacemethods.I";
    test("desugared-private-interface-methods",
        "privateinterfacemethods", "PrivateInterfaceMethods")
        .withMinApiLevel(AndroidApiLevel.M.getLevel())
        .withDexCheck(dexInspector -> {
          ClassSubject companion = dexInspector.clazz(
              iName + InterfaceMethodRewriter.COMPANION_CLASS_NAME_SUFFIX);
          assertThat(companion, isPresent());
          MethodSubject iFoo = companion.method(
              "java.lang.String",
              InterfaceMethodRewriter.PRIVATE_METHOD_PREFIX + "iFoo",
              ImmutableList.of(iName, "boolean"));
          assertThat(iFoo, isPresent());
          assertTrue(iFoo.getMethod().isPublicMethod());
        })
        .run();
  }

  @Test
  public void varHandle() throws Throwable {
    test("varhandle", "varhandle", "VarHandleTests")
        .withMinApiLevel(AndroidApiLevel.P.getLevel())
        .run();
  }

  @Test
  public void varHandleErrorDueToMinSdk() throws Throwable {
    test("varhandle-error-due-to-min-sdk", "varhandle", "VarHandleTests")
        .withMinApiLevel(AndroidApiLevel.O.getLevel())
        .run();
  }

  @Test
  public void testTwrCloseResourceMethod() throws Throwable {
    TestRunner<?> test = test("twr-close-resource", "twrcloseresource", "TwrCloseResourceTest");
    test
        .withMinApiLevel(AndroidApiLevel.I.getLevel())
        .withAndroidJar(AndroidApiLevel.K.getLevel())
        .withArg(test.getInputJar().toAbsolutePath().toString())
        .run();
  }

  abstract RunExamplesJava9Test<B>.TestRunner<?> test(String testName, String packageName,
      String mainClass);

  void execute(String testName,
      String qualifiedMainClass, Path[] jars, Path[] dexes, List<String> args) throws IOException {

    boolean expectedToFail = expectedToFail(testName);
    if (expectedToFail) {
      thrown.expect(Throwable.class);
    }
    String output = ToolHelper.runArtNoVerificationErrors(
        Arrays.stream(dexes).map(Path::toString).collect(Collectors.toList()),
        qualifiedMainClass,
        builder -> {
          for (String arg : args) {
            builder.appendProgramArgument(arg);
          }
        });
    String jvmResult = null;
    if (expectedJvmResult.containsKey(testName)) {
      jvmResult = expectedJvmResult.get(testName);
    } else if (!expectedToFail) {
      ToolHelper.ProcessResult javaResult =
          ToolHelper.runJava(ImmutableList.copyOf(jars), qualifiedMainClass);
      assertEquals("JVM run failed", javaResult.exitCode, 0);
      jvmResult = javaResult.stdout;
    }

    if (jvmResult != null) {
      assertTrue(
          "JVM output does not match art output.\n\tjvm: "
              + jvmResult
              + "\n\tart: "
              + output.replace("\r", ""),
          output.equals(jvmResult.replace("\r", "")));
    }
  }

}
