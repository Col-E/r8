// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.VmTestRunner.IgnoreIfVmOlderThan;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.OffOrAuto;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class R8RunExamplesAndroidOTest extends RunExamplesAndroidOTest<R8Command.Builder> {

  private static final ArrayList<String> PROGUARD_OPTIONS = Lists.newArrayList(
      "-keepclasseswithmembers public class * {",
      "    public static void main(java.lang.String[]);",
      "}",
      "-dontobfuscate",
      "-allowaccessmodification"
  );

  private static final ArrayList<String> PROGUARD_OPTIONS_N_PLUS =
      Lists.newArrayList(
          "-keepclasseswithmembers public class * {",
          "    public static void main(java.lang.String[]);",
          "}",
          "-keepclasseswithmembers interface **$AnnotatedInterface { <methods>; }",
          "-neverinline interface **$AnnotatedInterface { static void annotatedStaticMethod(); }",
          "-keepattributes *Annotation*",
          "-dontobfuscate",
          "-allowaccessmodification");

  private static Map<DexVm.Version, List<String>> alsoFailsOn =
      ImmutableMap.<DexVm.Version, List<String>>builder()
          .put(
              Version.V4_0_4,
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(
              Version.V4_4_4,
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(
              Version.V5_1_1,
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(
              Version.V6_0_1,
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(
              Version.V7_0_0,
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(
              Version.V9_0_0,
              // TODO(120402963) Triage.
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(
              Version.V10_0_0,
              // TODO(120402963) Triage.
              ImmutableList.of("invokecustom-with-shrinking", "invokecustom2-with-shrinking"))
          .put(Version.DEFAULT, ImmutableList.of())
          .build();

  @Test
  public void invokeCustomWithShrinking() throws Throwable {
    test("invokecustom-with-shrinking", "invokecustom", "InvokeCustom")
        .withMinApiLevel(AndroidApiLevel.O)
        .withBuilderTransformation(builder ->
            builder.addProguardConfigurationFiles(
                Paths.get(ToolHelper.EXAMPLES_ANDROID_O_DIR, "invokecustom/keep-rules.txt")))
        .run();
  }

  @Test
  public void invokeCustom2WithShrinking() throws Throwable {
    test("invokecustom2-with-shrinking", "invokecustom2", "InvokeCustom")
        .withMinApiLevel(AndroidApiLevel.O)
        .withBuilderTransformation(builder ->
            builder.addProguardConfigurationFiles(
                Paths.get(ToolHelper.EXAMPLES_ANDROID_O_DIR, "invokecustom2/keep-rules.txt")))
        .run();
  }

  @Override
  @Test
  public void lambdaDesugaring() throws Throwable {
    test("lambdadesugaring", "lambdadesugaring", "LambdaDesugaring")
        .withMinApiLevel(ToolHelper.getMinApiLevelForDexVmNoHigherThan(AndroidApiLevel.K))
        .withOptionConsumer(opts -> opts.enableClassInlining = false)
        .withBuilderTransformation(
            b -> b.addProguardConfiguration(PROGUARD_OPTIONS, Origin.unknown()))
        .withDexCheck(inspector -> checkLambdaCount(inspector, 102, "lambdadesugaring"))
        .run();

    test("lambdadesugaring", "lambdadesugaring", "LambdaDesugaring")
        .withMinApiLevel(ToolHelper.getMinApiLevelForDexVmNoHigherThan(AndroidApiLevel.K))
        .withOptionConsumer(opts -> opts.enableClassInlining = true)
        .withBuilderTransformation(
            b -> b.addProguardConfiguration(PROGUARD_OPTIONS, Origin.unknown()))
        .withDexCheck(inspector -> checkLambdaCount(inspector, 7, "lambdadesugaring"))
        .run();
  }

  @Test
  public void testMultipleInterfacesLambdaOutValue() throws Throwable {
    // We can only remove trivial check casts for the lambda objects if we keep track all the
    // multiple interfaces we additionally specified for the lambdas
    test("lambdadesugaring", "lambdadesugaring", "LambdaDesugaring")
        .withMinApiLevel(ToolHelper.getMinApiLevelForDexVmNoHigherThan(AndroidApiLevel.K))
        .withBuilderTransformation(
            b -> b.addProguardConfiguration(PROGUARD_OPTIONS, Origin.unknown()))
        .withBuilderTransformation(
            b ->
                b.addProguardConfiguration(
                    ImmutableList.of(
                        "-keep class lambdadesugaring.LambdaDesugaring {",
                        "  void testMultipleInterfaces();",
                        "}"),
                    Origin.unknown()))
        .withDexCheck(inspector -> checkTestMultipleInterfacesCheckCastCount(inspector, 0))
        .run();
  }

  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void lambdaDesugaringWithDefaultMethods() throws Throwable {
    test("lambdadesugaring", "lambdadesugaring", "LambdaDesugaring")
        .withMinApiLevel(AndroidApiLevel.N)
        .withOptionConsumer(opts -> opts.enableClassInlining = false)
        .withBuilderTransformation(
            b -> b.addProguardConfiguration(PROGUARD_OPTIONS, Origin.unknown()))
        .withDexCheck(inspector -> checkLambdaCount(inspector, 102, "lambdadesugaring"))
        .run();

    test("lambdadesugaring", "lambdadesugaring", "LambdaDesugaring")
        .withMinApiLevel(AndroidApiLevel.N)
        .withOptionConsumer(opts -> opts.enableClassInlining = true)
        .withBuilderTransformation(
            b -> b.addProguardConfiguration(PROGUARD_OPTIONS, Origin.unknown()))
        .withDexCheck(inspector -> checkLambdaCount(inspector, 7, "lambdadesugaring"))
        .run();
  }

  @Override
  @Test
  public void lambdaDesugaringNPlus() throws Throwable {
    test("lambdadesugaringnplus", "lambdadesugaringnplus", "LambdasWithStaticAndDefaultMethods")
        .withMinApiLevel(ToolHelper.getMinApiLevelForDexVmNoHigherThan(AndroidApiLevel.K))
        .withInterfaceMethodDesugaring(OffOrAuto.Auto)
        .withOptionConsumer(opts -> opts.enableClassInlining = false)
        .withBuilderTransformation(ToolHelper::allowTestProguardOptions)
        .withBuilderTransformation(
            b -> b.addProguardConfiguration(PROGUARD_OPTIONS_N_PLUS, Origin.unknown()))
        .withDexCheck(inspector -> checkLambdaCount(inspector, 35, "lambdadesugaringnplus"))
        .run();

    test("lambdadesugaringnplus", "lambdadesugaringnplus", "LambdasWithStaticAndDefaultMethods")
        .withMinApiLevel(ToolHelper.getMinApiLevelForDexVmNoHigherThan(AndroidApiLevel.K))
        .withInterfaceMethodDesugaring(OffOrAuto.Auto)
        .withOptionConsumer(opts -> opts.enableClassInlining = true)
        .withBuilderTransformation(ToolHelper::allowTestProguardOptions)
        .withBuilderTransformation(
            b -> b.addProguardConfiguration(PROGUARD_OPTIONS_N_PLUS, Origin.unknown()))
        .withDexCheck(inspector -> checkLambdaCount(inspector, 3, "lambdadesugaringnplus"))
        .run();
  }

  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void lambdaDesugaringNPlusWithDefaultMethods() throws Throwable {
    test("lambdadesugaringnplus", "lambdadesugaringnplus", "LambdasWithStaticAndDefaultMethods")
        .withMinApiLevel(AndroidApiLevel.N)
        .withInterfaceMethodDesugaring(OffOrAuto.Auto)
        .withOptionConsumer(opts -> opts.enableClassInlining = false)
        .withBuilderTransformation(ToolHelper::allowTestProguardOptions)
        .withBuilderTransformation(
            b -> b.addProguardConfiguration(PROGUARD_OPTIONS_N_PLUS, Origin.unknown()))
        .withDexCheck(inspector -> checkLambdaCount(inspector, 35, "lambdadesugaringnplus"))
        .run();

    test("lambdadesugaringnplus", "lambdadesugaringnplus", "LambdasWithStaticAndDefaultMethods")
        .withMinApiLevel(AndroidApiLevel.N)
        .withInterfaceMethodDesugaring(OffOrAuto.Auto)
        .withOptionConsumer(opts -> opts.enableClassInlining = true)
        .withBuilderTransformation(ToolHelper::allowTestProguardOptions)
        .withBuilderTransformation(
            b -> b.addProguardConfiguration(PROGUARD_OPTIONS_N_PLUS, Origin.unknown()))
        .withDexCheck(inspector -> checkLambdaCount(inspector, 2, "lambdadesugaringnplus"))
        .run();
  }

  private void checkLambdaCount(CodeInspector inspector, int maxExpectedCount, String prefix) {
    int count = 0;
    for (FoundClassSubject clazz : inspector.allClasses()) {
      if (clazz.isSynthesizedJavaLambdaClass() &&
          clazz.getOriginalName().startsWith(prefix)) {
        count++;
      }
    }
    assertEquals(maxExpectedCount, count);
  }

  private void checkTestMultipleInterfacesCheckCastCount(
      CodeInspector inspector, int expectedCount) {
    ClassSubject clazz = inspector.clazz("lambdadesugaring.LambdaDesugaring");
    assert clazz.isPresent();
    MethodSubject method = clazz.method("void", "testMultipleInterfaces");
    assert method.isPresent();
    class Count {
      int i = 0;
    }
    final Count count = new Count();
    method
        .iterateInstructions(InstructionSubject::isCheckCast)
        .forEachRemaining(
            instruction -> {
              ++count.i;
            });
    assertEquals(expectedCount, count.i);
  }

  class R8TestRunner extends TestRunner<R8TestRunner> {

    R8TestRunner(String testName, String packageName, String mainClass) {
      super(testName, packageName, mainClass);
    }

    @Override
    R8TestRunner withMinApiLevel(AndroidApiLevel minApiLevel) {
      return withBuilderTransformation(builder -> builder.setMinApiLevel(minApiLevel.getLevel()));
    }

    @Override R8TestRunner withKeepAll() {
      return withBuilderTransformation(builder ->
          builder
              .setDisableTreeShaking(true)
              .setDisableMinification(true)
              .addProguardConfiguration(ImmutableList.of("-keepattributes *"), Origin.unknown()));
    }

    @Override
    void build(Path inputFile, Path out, OutputMode mode) throws Throwable {
      R8Command.Builder builder = R8Command.builder().setOutput(out, mode);
      for (UnaryOperator<R8Command.Builder> transformation : builderTransformations) {
        builder = transformation.apply(builder);
      }

      builder.addLibraryFiles(ToolHelper.getAndroidJar(
          androidJarVersion == null ? builder.getMinApiLevel() : androidJarVersion.getLevel()));
      R8Command command = builder.addProgramFiles(inputFile).build();
      ToolHelper.runR8(command, this::combinedOptionConsumer);
    }

    @Override
    R8TestRunner self() {
      return this;
    }
  }

  @Override
  R8TestRunner test(String testName, String packageName, String mainClass) {
    return new R8TestRunner(testName, packageName, mainClass);
  }

  @Override
  boolean expectedToFail(String name) {
    return super.expectedToFail(name) || failsOn(alsoFailsOn, name);
  }
}
