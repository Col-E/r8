// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.assumevalues;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.shaking.ProguardAssumeValuesRule;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SynthesizedRulesFromApiLevelTest extends TestBase {

  private final Backend backend;
  private final String mainClassName = "MainClass";
  private final String compatLibraryClassName = "CompatLibrary";

  public SynthesizedRulesFromApiLevelTest(Backend backend) {
    this.backend = backend;
  }

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  // Simple mock implementation of class android.os.Build$VERSION with just the SDK_INT field.
  private Path mockAndroidRuntimeLibrary(int sdkInt) throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder;

    classBuilder = builder.addClass("android.os.Build$VERSION");
    classBuilder.addStaticFinalField("SDK_INT", "I", Integer.toString(sdkInt));

    classBuilder = builder.addClass("android.os.Native");
    classBuilder.addStaticMethod("method", ImmutableList.of(), "V",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  ldc \" Native\"",
        "  invokevirtual java/io/PrintStream/print(Ljava.lang.String;)V",
        "  return"
    );

    return writeToJar(builder);
  }

  private Path buildMockAndroidRuntimeLibrary(AndroidApiLevel apiLevel) throws Exception {
    // Build the mock library containing android.os.Build.VERSION with D8.
    Path library = temp.newFolder().toPath().resolve("library.jar");
    D8.run(
        D8Command
            .builder()
            .addProgramFiles(mockAndroidRuntimeLibrary(apiLevel.getLevel()))
            .setOutput(library, OutputMode.DexIndexed)
            .build());
    return library;
  }

  private Path buildApp(AndroidApiLevel apiLevelForNative) throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder;

    classBuilder = builder.addClass(compatLibraryClassName);

    classBuilder.addStaticMethod("compatMethod", ImmutableList.of(), "V",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  ldc \" Compat\"",
        "  invokevirtual java/io/PrintStream/print(Ljava.lang.String;)V",
        "  return"
    );

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
      if (rule instanceof ProguardAssumeValuesRule
          && rule.getOrigin().part().contains("SYNTHESIZED_FROM_API_LEVEL")) {
        assertEquals(expected, SynthesizedRule.PRESENT);
        return;
      }
    }
    assertEquals(expected, SynthesizedRule.NOT_PRESENT);
  }

  private void noSynthesizedRules(ProguardConfiguration proguardConfiguration) {
    for (ProguardConfigurationRule rule : proguardConfiguration.getRules()) {
      if (rule instanceof ProguardAssumeValuesRule) {
        assertFalse(rule.getOrigin().part().contains("SYNTHESIZED_FROM_API_LEVEL"));
      }
    }
  }

  private void runTest(
      AndroidApiLevel buildApiLevel,
      AndroidApiLevel runtimeApiLevel,
      AndroidApiLevel nativeApiLevel,
      String expectedOutput,
      Consumer<CodeInspector> inspector,
      List<String> additionalKeepRules,
      SynthesizedRule synthesizedRule) throws Exception{
    assertTrue(runtimeApiLevel.getLevel() >= buildApiLevel.getLevel());
    if (backend == Backend.DEX) {
      testForR8(backend)
          .setMinApi(buildApiLevel)
          .addProgramFiles(buildApp(nativeApiLevel))
          .enableProguardTestOptions()
          .addKeepRules("-neverinline class " + compatLibraryClassName + " { *; }")
          .addKeepMainRule(mainClassName)
          .addKeepRules(additionalKeepRules)
          .compile()
          .inspectSyntheticProguardRules(
              syntheticProguardRules ->
                  checkSynthesizedRuleExpectation(syntheticProguardRules, synthesizedRule))
          .inspect(inspector)
          .addRunClasspath(ImmutableList.of(buildMockAndroidRuntimeLibrary(runtimeApiLevel)))
          .run(mainClassName)
          .assertSuccessWithOutput(expectedOutput);
    } else {
      assert backend == Backend.CF;
      testForR8(backend)
          .addProgramFiles(buildApp(nativeApiLevel))
          .addKeepMainRule(mainClassName)
          .addKeepRules(additionalKeepRules)
          .compile()
          .inspectProguardConfiguration(this::noSynthesizedRules)
          .addRunClasspath(
              ImmutableList.of(mockAndroidRuntimeLibrary(AndroidApiLevel.D.getLevel())))
          .run(mainClassName)
          .assertSuccessWithOutput(expectedResultForCompat(AndroidApiLevel.D));
    }
  }

  private void runTest(
      AndroidApiLevel buildApiLevel,
      AndroidApiLevel runtimeApiLevel,
      AndroidApiLevel nativeApiLevel,
      String expectedOutput,
      Consumer<CodeInspector> inspector) throws Exception{
    runTest(
        buildApiLevel,
        runtimeApiLevel,
        nativeApiLevel,
        expectedOutput,
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
    assertThat(compatLibrary.uniqueMethodWithName("compatMethod"), isPresent());
  }

  private void compatCodeNotPresent(CodeInspector inspector) {
    ClassSubject compatLibrary = inspector.clazz(compatLibraryClassName);
    assertThat(compatLibrary, isPresent());
    assertThat(compatLibrary.uniqueMethodWithName("compatMethod"), not(isPresent()));
  }

  @Test
  public void testNoExplicitAssumeValuesRuleNative() throws Exception {
    runTest(
        AndroidApiLevel.O_MR1,
        AndroidApiLevel.O_MR1,
        AndroidApiLevel.O_MR1,
        expectedResultForNative(AndroidApiLevel.O_MR1),
        this::compatCodeNotPresent);
  }

  @Test
  public void testNoExplicitAssumeValuesRuleCompatPresent() throws Exception {
    runTest(
        AndroidApiLevel.O,
        AndroidApiLevel.O_MR1,
        AndroidApiLevel.O_MR1,
        expectedResultForNative(AndroidApiLevel.O_MR1),
        this::compatCodePresent);
  }

  @Test
  public void testNoExplicitAssumeValuesRuleCompatUsed() throws Exception {
    runTest(
        AndroidApiLevel.O,
        AndroidApiLevel.O,
        AndroidApiLevel.O_MR1,
        expectedResultForCompat(AndroidApiLevel.O),
        this::compatCodePresent);
  }

  @Test
  public void testExplicitAssumeValuesRuleWhichMatchAndDontKeepCompat() throws Exception {
    runTest(
        AndroidApiLevel.O_MR1,
        AndroidApiLevel.O_MR1,
        AndroidApiLevel.O_MR1,
        expectedResultForNative(AndroidApiLevel.O_MR1),
        this::compatCodeNotPresent,
        ImmutableList.of(
            "-assumevalues class android.os.Build$VERSION { public static final int SDK_INT return "
            + AndroidApiLevel.O_MR1.getLevel() + "..1000; }"),
        SynthesizedRule.NOT_PRESENT);
  }

  @Test
  public void testExplicitAssumeValuesRulesWhichMatchAndKeepCompat() throws Exception {
    String[] rules = new String[] {
        "-assumevalues class * { int SDK_INT return 1..1000; }",
        "-assumevalues class * { % SDK_INT return 1..1000; }",
        "-assumevalues class * { int * return 1..1000; }",
        "-assumevalues class * extends java.lang.Object { int SDK_INT return 1..1000; }",
        "-assumevalues class * { <fields>; }",
        "-assumevalues class * { *; }"
    };

    for (String rule : rules) {
      runTest(
          AndroidApiLevel.O_MR1,
          AndroidApiLevel.O_MR1,
          AndroidApiLevel.O_MR1,
          expectedResultForNative(AndroidApiLevel.O_MR1),
          this::compatCodePresent,
          ImmutableList.of(rule),
          SynthesizedRule.NOT_PRESENT);
    }
  }

  @Test
  public void testExplicitAssumeValuesRulesWhichDoesNotMatch() throws Exception {
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
          this::compatCodeNotPresent,
          ImmutableList.of(rule),
          SynthesizedRule.PRESENT);
    }
  }
}
