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
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.FoundClassSubject;
import com.android.tools.r8.utils.OffOrAuto;
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
      "",
      "-dontobfuscate",
      "-allowaccessmodification"
  );


  private static final ArrayList<String> PROGUARD_OPTIONS_N_PLUS = Lists.newArrayList(
      "-keepclasseswithmembers public class * {",
      "    public static void main(java.lang.String[]);",
      "}",
      "",
      "-keepclasseswithmembers interface lambdadesugaringnplus."
          + "LambdasWithStaticAndDefaultMethods$B38302860$AnnotatedInterface{",
      "    *;",
      "} ",
      "",
      "-keepattributes *Annotation*",
      "-dontobfuscate",
      "-allowaccessmodification"
  );

  private static Map<DexVm.Version, List<String>> alsoFailsOn =
      ImmutableMap.<DexVm.Version, List<String>>builder()
          .put(Version.V4_0_4,
              ImmutableList.of(
                  "invokecustom-with-shrinking"
              ))
          .put(Version.V4_4_4,
              ImmutableList.of(
                  "invokecustom-with-shrinking"
              ))
          .put(Version.V5_1_1,
              ImmutableList.of(
                  "invokecustom-with-shrinking"
              ))
          .put(Version.V6_0_1,
              ImmutableList.of(
                  "invokecustom-with-shrinking"
              ))
          .put(Version.V7_0_0,
              ImmutableList.of(
                  "invokecustom-with-shrinking"
              ))
          .put(Version.DEFAULT,
              ImmutableList.of(
              ))
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

  @Override
  @Test
  public void lambdaDesugaring() throws Throwable {
    test("lambdadesugaring", "lambdadesugaring", "LambdaDesugaring")
        .withMinApiLevel(AndroidApiLevel.K)
        .withOptionConsumer(opts -> opts.enableClassInlining = false)
        .withBuilderTransformation(
            b -> b.addProguardConfiguration(PROGUARD_OPTIONS, Origin.unknown()))
        .withDexCheck(inspector -> checkLambdaCount(inspector, 179, "lambdadesugaring"))
        .run();

    test("lambdadesugaring", "lambdadesugaring", "LambdaDesugaring")
        .withMinApiLevel(AndroidApiLevel.K)
        .withOptionConsumer(opts -> opts.enableClassInlining = true)
        .withBuilderTransformation(
            b -> b.addProguardConfiguration(PROGUARD_OPTIONS, Origin.unknown()))
        .withDexCheck(inspector -> checkLambdaCount(inspector, 23, "lambdadesugaring"))
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
        .withDexCheck(inspector -> checkLambdaCount(inspector, 179, "lambdadesugaring"))
        .run();

    test("lambdadesugaring", "lambdadesugaring", "LambdaDesugaring")
        .withMinApiLevel(AndroidApiLevel.N)
        .withOptionConsumer(opts -> opts.enableClassInlining = true)
        .withBuilderTransformation(
            b -> b.addProguardConfiguration(PROGUARD_OPTIONS, Origin.unknown()))
        .withDexCheck(inspector -> checkLambdaCount(inspector, 23, "lambdadesugaring"))
        .run();
  }

  @Override
  @Test
  public void lambdaDesugaringNPlus() throws Throwable {
    test("lambdadesugaringnplus", "lambdadesugaringnplus", "LambdasWithStaticAndDefaultMethods")
        .withMinApiLevel(AndroidApiLevel.K)
        .withInterfaceMethodDesugaring(OffOrAuto.Auto)
        .withOptionConsumer(opts -> opts.enableClassInlining = false)
        .withBuilderTransformation(
            b -> b.addProguardConfiguration(PROGUARD_OPTIONS_N_PLUS, Origin.unknown()))
        .withDexCheck(inspector -> checkLambdaCount(inspector, 40, "lambdadesugaringnplus"))
        .run();

    test("lambdadesugaringnplus", "lambdadesugaringnplus", "LambdasWithStaticAndDefaultMethods")
        .withMinApiLevel(AndroidApiLevel.K)
        .withInterfaceMethodDesugaring(OffOrAuto.Auto)
        .withOptionConsumer(opts -> opts.enableClassInlining = true)
        .withBuilderTransformation(
            b -> b.addProguardConfiguration(PROGUARD_OPTIONS_N_PLUS, Origin.unknown()))
        .withDexCheck(inspector -> checkLambdaCount(inspector, 5, "lambdadesugaringnplus"))
        .run();
  }

  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void lambdaDesugaringNPlusWithDefaultMethods() throws Throwable {
    test("lambdadesugaringnplus", "lambdadesugaringnplus", "LambdasWithStaticAndDefaultMethods")
        .withMinApiLevel(AndroidApiLevel.N)
        .withInterfaceMethodDesugaring(OffOrAuto.Auto)
        .withOptionConsumer(opts -> opts.enableClassInlining = false)
        .withBuilderTransformation(
            b -> b.addProguardConfiguration(PROGUARD_OPTIONS_N_PLUS, Origin.unknown()))
        .withDexCheck(inspector -> checkLambdaCount(inspector, 40, "lambdadesugaringnplus"))
        .run();

    test("lambdadesugaringnplus", "lambdadesugaringnplus", "LambdasWithStaticAndDefaultMethods")
        .withMinApiLevel(AndroidApiLevel.N)
        .withInterfaceMethodDesugaring(OffOrAuto.Auto)
        .withOptionConsumer(opts -> opts.enableClassInlining = true)
        .withBuilderTransformation(
            b -> b.addProguardConfiguration(PROGUARD_OPTIONS_N_PLUS, Origin.unknown()))
        .withDexCheck(inspector -> checkLambdaCount(inspector, 5, "lambdadesugaringnplus"))
        .run();
  }

  private void checkLambdaCount(DexInspector inspector, int expectedCount, String prefix) {
    int count = 0;
    for (FoundClassSubject clazz : inspector.allClasses()) {
      if (clazz.isSynthesizedJavaLambdaClass() &&
          clazz.getOriginalName().startsWith(prefix)) {
        count++;
      }
    }
    assertEquals(expectedCount, count);
  }

  class R8TestRunner extends TestRunner<R8TestRunner> {

    R8TestRunner(String testName, String packageName, String mainClass) {
      super(testName, packageName, mainClass);
    }

    @Override
    R8TestRunner withMinApiLevel(AndroidApiLevel minApiLevel) {
      return withBuilderTransformation(builder -> builder.setMinApiLevel(minApiLevel.getLevel()));
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
