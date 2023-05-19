// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringForTesting.getCompanionClassNameSuffix;
import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringForTesting.getPrivateMethodPrefix;
import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.ZIP_EXTENSION;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.TestDescriptionWatcher;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public abstract class RunExamplesJava9Test<B extends BaseCommand.Builder<? extends BaseCommand, B>>
    extends TestBase {

  private static final String EXAMPLE_DIR = ToolHelper.EXAMPLES_JAVA9_BUILD_DIR;

  abstract class TestRunner<C extends TestRunner<C>> {
    final String testName;
    final String packageName;
    final String mainClass;
    final List<String> args = new ArrayList<>();

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

    C withArg(String arg) {
      args.add(arg);
      return self();
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

    abstract C withMinApiLevel(int minApiLevel);

    C withKeepAll() {
      return self();
    }

    abstract void build(Path inputFile, Path out) throws Throwable;
  }

  private static List<String> minSdkErrorExpected = ImmutableList.of();

  private static Map<DexVm.Version, List<String>> failsOn;

  static {
    ImmutableMap.Builder<DexVm.Version, List<String>> builder = ImmutableMap.builder();
    builder
        .put(DexVm.Version.V4_0_4, ImmutableList.of("native-private-interface-methods"))
        .put(DexVm.Version.V4_4_4, ImmutableList.of("native-private-interface-methods"))
        .put(DexVm.Version.V5_1_1, ImmutableList.of("native-private-interface-methods"))
        .put(DexVm.Version.V6_0_1, ImmutableList.of("native-private-interface-methods"));
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
          "0: s>i>a\n" + "1: d>i>s>i>a\n" + "2: l>i>s>i>a\n" + "3: x>s\n" + "4: c>d>i>s>i>a\n",
          "desugared-private-interface-methods",
          "0: s>i>a\n" + "1: d>i>s>i>a\n" + "2: l>i>s>i>a\n" + "3: x>s\n" + "4: c>d>i>s>i>a\n");

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Rule
  public TestDescriptionWatcher watcher = new TestDescriptionWatcher();

  private boolean failsOn(Map<DexVm.Version, List<String>> failsOn, String name) {
    DexVm.Version vmVersion = ToolHelper.getDexVm().getVersion();
    return failsOn.containsKey(vmVersion)
        && failsOn.get(vmVersion).contains(name);
  }

  private boolean expectedToFail(String name) {
    return failsOn(failsOn, name);
  }

  private boolean minSdkErrorExpected(String testName) {
    return minSdkErrorExpected.contains(testName);
  }

  @Test
  public void nativePrivateInterfaceMethods() throws Throwable {
    test("native-private-interface-methods",
        "privateinterfacemethods", "PrivateInterfaceMethods")
        .withMinApiLevel(AndroidApiLevel.N.getLevel())
        .withKeepAll()
        .run();
  }

  @Test
  public void desugaredPrivateInterfaceMethods() throws Throwable {
    assumeFalse("CF backend does not desugar", this instanceof R8CFRunExamplesJava9Test);
    final String iName = "privateinterfacemethods.I";
    test(
            "desugared-private-interface-methods",
            "privateinterfacemethods",
            "PrivateInterfaceMethods")
        .withMinApiLevel(AndroidApiLevel.K.getLevel())
        .withKeepAll()
        .withDexCheck(
            dexInspector -> {
              ClassSubject companion = dexInspector.clazz(iName + getCompanionClassNameSuffix());
              assertThat(companion, isPresent());
              MethodSubject iFoo =
                  companion.method(
                      "java.lang.String",
                      getPrivateMethodPrefix() + "iFoo",
                      ImmutableList.of(iName, "boolean"));
              assertThat(iFoo, isPresent());
              assertTrue(iFoo.getMethod().isPublicMethod());
            })
        .run();
  }

  @Test
  public void testTwrCloseResourceMethod() throws Throwable {
    TestRunner<?> test = test("twr-close-resource", "twrcloseresource", "TwrCloseResourceTest");
    test
        .withMinApiLevel(AndroidApiLevel.I.getLevel())
        .withKeepAll()
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
