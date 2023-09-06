// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.assumevalues;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.shaking.ProguardAssumeNoSideEffectRule;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SynthesizedRulesFromApiLevelTest extends TestBase {

  private static final String mainClassName = "MainClass";
  private static final String compatLibraryClassName = "CompatLibrary";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().build();
  }

  // Simple mock implementation of class android.os.Build$VERSION with just the SDK_INT field.
  private Path mockAndroidRuntimeLibrary(AndroidApiLevel apiLevel) throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder;

    classBuilder = builder.addClass("android.os.Build$VERSION");
    classBuilder.addStaticFinalField("SDK_INT", "I", Integer.toString(apiLevel.getLevel()));

    classBuilder = builder.addClass("android.os.Native");
    classBuilder.addStaticMethod("method", ImmutableList.of(), "V",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  ldc \" Native\"",
        "  invokevirtual java/io/PrintStream/print(Ljava.lang.String;)V",
        "  return"
    );

    return writeToJar(builder);
  }

  private Path buildMockAndroidRuntimeLibrary(Path mockAndroidRuntimeLibrary) throws Exception {
    // Build the mock library containing android.os.Build.VERSION with D8.
    Path library = temp.newFolder().toPath().resolve("library.jar");
    D8.run(
        D8Command.builder()
            .addProgramFiles(mockAndroidRuntimeLibrary)
            .setOutput(library, OutputMode.DexIndexed)
            .build());
    return library;
  }

  private Path buildApp(AndroidApiLevel apiLevelForNative) throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder;

    classBuilder = builder.addClass(compatLibraryClassName);

    classBuilder.addStaticMethod(
        "compatMethod",
        ImmutableList.of(),
        "V",
        ".limit stack 2",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  ldc \" Compat\"",
        "  invokevirtual java/io/PrintStream/print(Ljava.lang.String;)V",
        "  return");

    classBuilder.addStaticMethod("method", ImmutableList.of(), "V",
        ".limit stack 2",
        "  getstatic android/os/Build$VERSION/SDK_INT I",
        "  ldc " + apiLevelForNative.getLevel(),
        "if_icmpge Native",
        "  invokestatic " + compatLibraryClassName +"/compatMethod()V",
        "  return",
        "Native:",
        "  invokestatic android.os.Native/method()V",
        "  return"
    );

    classBuilder = builder.addClass(mainClassName);

    classBuilder.addStaticMethod("getSdkInt", ImmutableList.of(), "I",
        ".limit stack 1",
        "getstatic android/os/Build$VERSION/SDK_INT I",
        "ireturn"
    );

    classBuilder.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",

        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "invokestatic " + mainClassName + "/getSdkInt()I",
        "invokevirtual java/io/PrintStream/print(I)V",

        "invokestatic " + compatLibraryClassName + "/method()V",

        "return");

    return writeToJar(builder);
  }

  private enum SynthesizedRule {
    PRESENT,
    NOT_PRESENT
  }

  private void checkSynthesizedRuleExpectation(
      List<ProguardConfigurationRule> synthesizedRules, SynthesizedRule expected) {
    for (ProguardConfigurationRule rule : synthesizedRules) {
      if (rule instanceof ProguardAssumeNoSideEffectRule
          && rule.getOrigin().part().contains("SYNTHESIZED_FROM_API_LEVEL")) {
        assertEquals(expected, SynthesizedRule.PRESENT);
        return;
      }
    }
    assertEquals(expected, SynthesizedRule.NOT_PRESENT);
  }

  private void noSynthesizedRules(List<ProguardConfigurationRule> synthesizedRules) {
    assertTrue(synthesizedRules.isEmpty());
  }

  private void runTest(
      AndroidApiLevel buildApiLevel,
      AndroidApiLevel runtimeApiLevel,
      AndroidApiLevel nativeApiLevel,
      String expectedOutput,
      ThrowableConsumer<R8FullTestBuilder> configuration,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector,
      List<String> additionalKeepRules,
      SynthesizedRule synthesizedRule)
      throws Exception {
    assertTrue(runtimeApiLevel.getLevel() >= buildApiLevel.getLevel());
    if (parameters.isDexRuntime()) {
      Path androidRuntimeLibraryMock = mockAndroidRuntimeLibrary(runtimeApiLevel);
      testForR8(parameters.getBackend())
          .setMinApi(buildApiLevel)
          .addProgramFiles(buildApp(nativeApiLevel))
          .addClasspathFiles(androidRuntimeLibraryMock)
          .enableProguardTestOptions()
          .addKeepRules("-neverinline class " + compatLibraryClassName + " { *; }")
          .addKeepMainRule(mainClassName)
          .addKeepRules(additionalKeepRules)
          .apply(configuration)
          .compile()
          .inspectSyntheticProguardRules(
              syntheticProguardRules ->
                  checkSynthesizedRuleExpectation(syntheticProguardRules, synthesizedRule))
          .inspect(inspector)
          .addRunClasspathFiles(buildMockAndroidRuntimeLibrary(androidRuntimeLibraryMock))
          .run(parameters.getRuntime(), mainClassName)
          .assertSuccessWithOutput(expectedOutput);
    } else {
      Path androidRuntimeLibraryMock = mockAndroidRuntimeLibrary(AndroidApiLevel.D);
      testForR8(parameters.getBackend())
          .addProgramFiles(buildApp(nativeApiLevel))
          .addKeepMainRule(mainClassName)
          .addKeepRules(additionalKeepRules)
          .addDontWarn("android.os.Build$VERSION", "android.os.Native")
          .apply(configuration)
          .compile()
          .inspectSyntheticProguardRules(this::noSynthesizedRules)
          .addRunClasspathFiles(androidRuntimeLibraryMock)
          .run(parameters.getRuntime(), mainClassName)
          .assertSuccessWithOutput(expectedResultForCompat(AndroidApiLevel.D));
    }
  }

  private void runTest(
      AndroidApiLevel buildApiLevel,
      AndroidApiLevel runtimeApiLevel,
      AndroidApiLevel nativeApiLevel,
      String expectedOutput,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector)
      throws Exception {
    runTest(
        buildApiLevel,
        runtimeApiLevel,
        nativeApiLevel,
        expectedOutput,
        null,
        inspector,
        ImmutableList.of(),
        SynthesizedRule.PRESENT);
  }

  private String expectedResultForNative(AndroidApiLevel runtimeApiLevel) {
    return runtimeApiLevel.getLevel() + " Native";
  }

  private String expectedResultForCompat(AndroidApiLevel runtimeApiLevel) {
    return runtimeApiLevel.getLevel() + " Compat";
  }

  private void compatCodePresent(CodeInspector inspector) {
    ClassSubject compatLibrary = inspector.clazz(compatLibraryClassName);
    assertThat(compatLibrary, isPresent());
    assertThat(compatLibrary.uniqueMethodWithOriginalName("compatMethod"), isPresent());
  }

  private void compatCodeNotPresent(CodeInspector inspector) {
    ClassSubject compatLibrary = inspector.clazz(compatLibraryClassName);
    assertThat(compatLibrary, isPresent());
    assertThat(compatLibrary.uniqueMethodWithOriginalName("compatMethod"), not(isPresent()));
  }

  @Test
  public void testNoExplicitAssumeValuesRuleNative() throws Exception {
    assumeTrue(
        parameters.isCfRuntime() || parameters.getDexRuntimeVersion().isNewerThan(Version.V7_0_0));
    runTest(
        AndroidApiLevel.O_MR1,
        AndroidApiLevel.O_MR1,
        AndroidApiLevel.O_MR1,
        expectedResultForNative(AndroidApiLevel.O_MR1),
        this::compatCodeNotPresent);
  }

  @Test
  public void testNoExplicitAssumeValuesRuleCompatPresent() throws Exception {
    assumeTrue(
        parameters.isCfRuntime() || parameters.getDexRuntimeVersion().isNewerThan(Version.V7_0_0));
    runTest(
        AndroidApiLevel.O,
        AndroidApiLevel.O_MR1,
        AndroidApiLevel.O_MR1,
        expectedResultForNative(AndroidApiLevel.O_MR1),
        this::compatCodePresent);
  }

  @Test
  public void testNoExplicitAssumeValuesRuleCompatUsed() throws Exception {
    assumeTrue(
        parameters.isCfRuntime() || parameters.getDexRuntimeVersion().isNewerThan(Version.V7_0_0));
    runTest(
        AndroidApiLevel.O,
        AndroidApiLevel.O,
        AndroidApiLevel.O_MR1,
        expectedResultForCompat(AndroidApiLevel.O),
        this::compatCodePresent);
  }

  @Test
  public void testExplicitAssumeValuesRuleWhichMatchAndDontKeepCompat() throws Exception {
    assumeTrue(
        parameters.isCfRuntime() || parameters.getDexRuntimeVersion().isNewerThan(Version.V7_0_0));
    runTest(
        AndroidApiLevel.O_MR1,
        AndroidApiLevel.O_MR1,
        AndroidApiLevel.O_MR1,
        expectedResultForNative(AndroidApiLevel.O_MR1),
        builder -> {
          // android.os.Build$VERSION only exists in the Android runtime.
          builder.allowUnusedProguardConfigurationRules(parameters.isCfRuntime());
        },
        this::compatCodeNotPresent,
        ImmutableList.of(
            "-assumevalues class android.os.Build$VERSION { public static final int SDK_INT return "
                + AndroidApiLevel.O_MR1.getLevel()
                + "..1000; }"),
        SynthesizedRule.NOT_PRESENT);
  }

  @Test
  public void testExplicitAssumeValuesRulesWhichMatchAndKeepCompat() throws Exception {
    assumeTrue(
        parameters.isCfRuntime() || parameters.getDexRuntimeVersion().isNewerThan(Version.V7_0_0));
    String[] rules =
        new String[] {
          "-assumevalues class android.os.Build$VERSION { int SDK_INT return 1..1000; }",
          "-assumevalues class android.os.Build$VERSION { % SDK_INT return 1..1000; }",
          "-assumevalues class android.os.Build$VERSION { int * return 1..1000; }",
          "-assumevalues class android.os.Build$VERSION extends java.lang.Object { int SDK_INT"
              + " return 1..1000; }",
          "-assumevalues class android.os.Build$VERSION { <fields>; }",
          "-assumevalues class android.os.Build$VERSION { *; }"
        };

    for (int ruleIndex = 0; ruleIndex < rules.length; ruleIndex++) {
      final int finalRuleIndex = ruleIndex;
      String rule = rules[ruleIndex];
      runTest(
          AndroidApiLevel.O_MR1,
          AndroidApiLevel.O_MR1,
          AndroidApiLevel.O_MR1,
          expectedResultForNative(AndroidApiLevel.O_MR1),
          builder ->
              builder.allowUnusedProguardConfigurationRules(
                  parameters.isCfRuntime() || finalRuleIndex >= 4),
          this::compatCodePresent,
          ImmutableList.of(rule),
          SynthesizedRule.NOT_PRESENT);
    }
  }

  @Test
  public void testExplicitAssumeValuesRulesWhichDoesNotMatch() throws Exception {
    assumeTrue(
        parameters.isCfRuntime() || parameters.getDexRuntimeVersion().isNewerThan(Version.V7_0_0));
    String[] rules = new String[] {
        "-assumevalues class * { !public int SDK_INT return 1..1000; }",
        "-assumevalues class * { !static int SDK_INT return 1..1000; }",
        "-assumevalues class * { !final int SDK_INT return 1..1000; }",
        "-assumevalues class * { protected int SDK_INT return 1..1000; }",
        "-assumevalues class * { private int SDK_INT return 1..1000; }"
    };
    for (String rule : rules) {
      runTest(
          AndroidApiLevel.O_MR1,
          AndroidApiLevel.O_MR1,
          AndroidApiLevel.O_MR1,
          expectedResultForNative(AndroidApiLevel.O_MR1),
          R8TestBuilder::allowUnusedProguardConfigurationRules,
          this::compatCodeNotPresent,
          ImmutableList.of(rule),
          SynthesizedRule.PRESENT);
    }
  }

  @Test
  public void testUnknownApiLevelRule() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    List<ProguardConfigurationRule> rules =
        testForR8(parameters.getBackend())
            .addProgramClasses(TestClass.class)
            .addKeepMainRule(TestClass.class)
            .setMinApi(AndroidApiLevel.ANDROID_PLATFORM_CONSTANT)
            .compile()
            .getSyntheticProguardRules();
    for (ProguardConfigurationRule rule : rules) {
      String ruleText = rule.toString();
      if (ruleText.contains("SDK_INT")) {
        assertThat(
            ruleText,
            containsString(
                "return " + AndroidApiLevel.UNKNOWN.getLevel() + ".." + Integer.MAX_VALUE));
        return;
      }
    }
    fail("Expected to find rule for SDK_INT value range");
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello!");
    }
  }
}
