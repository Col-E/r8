// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compatproguard;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.BaseCompilerCommand;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.compatproguard.CompatKeepClassMemberNamesTest.Bar;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for compatibility with Proguard -keepclassmember{s,names}. b/119076934. */
@RunWith(Parameterized.class)
public class CompatKeepClassMemberNamesTestRunner extends TestBase {

  private static final ProguardVersion PG = ProguardVersion.getLatest();

  private static Class<?> MAIN_CLASS = CompatKeepClassMemberNamesTest.class;
  private static Class<?> BAR_CLASS = CompatKeepClassMemberNamesTest.Bar.class;
  private static Collection<Class<?>> CLASSES =
      ImmutableList.of(MAIN_CLASS, BAR_CLASS, NeverInline.class);

  private static String KEEP_RULE =
      "class "
          + Bar.class.getTypeName()
          + " { static "
          + Bar.class.getTypeName()
          + " instance(); void <init>(); int i; }";

  private static String KEEP_RULE_NON_STATIC =
      "class " + Bar.class.getTypeName() + " { void <init>(); int i; }";

  private static String EXPECTED = StringUtils.lines("42", "null");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  private final TestParameters parameters;

  public CompatKeepClassMemberNamesTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  // Test reference implementation.

  @Test
  public void testJvm() throws Exception {
    testForJvm()
        .addProgramClasses(CLASSES)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }

  // Helpers to check that the Bar is absent or the Bar.instance() call has not been inlined.

  private <CR extends TestCompileResult<CR, RR>, RR extends TestRunResult<RR>>
      void assertBarIsAbsent(CR compileResult) throws Exception {
    compileResult
        .inspect(
            inspector -> {
              assertTrue(inspector.clazz(MAIN_CLASS).isPresent());
              assertFalse(inspector.clazz(BAR_CLASS).isPresent());
            })
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertFailureWithErrorThatMatches(
            allOf(
                containsString("ClassNotFoundException"), containsString(BAR_CLASS.getTypeName())));
  }

  private static void assertBarGetInstanceIsNotInlined(CodeInspector inspector) {
    assertTrue(inspector.clazz(BAR_CLASS).uniqueMethodWithName("instance").isPresent());
    assertTrue(
        inspector
            .clazz(MAIN_CLASS)
            .uniqueMethodWithName("main")
            .streamInstructions()
            .anyMatch(i -> i.isInvoke() && i.getMethod().qualifiedName().contains("instance")));
  }

  // Tests with just keep main and no additional rules.

  private <
          C extends BaseCompilerCommand,
          B extends BaseCompilerCommand.Builder<C, B>,
          CR extends TestCompileResult<CR, RR>,
          RR extends TestRunResult<RR>,
          T extends TestShrinkerBuilder<C, B, CR, RR, T>>
      void testWithoutRules(TestShrinkerBuilder<C, B, CR, RR, T> builder) throws Exception {
    assertBarIsAbsent(
        builder.addProgramClasses(CLASSES).addKeepMainRule(MAIN_CLASS).noMinification().compile());
  }

  @Test
  public void testWithoutRulesPG() throws Exception {
    testWithoutRules(testForProguard(PG));
  }

  @Test
  public void testWithoutRulesCompatR8() throws Exception {
    testWithoutRules(testForR8Compat(parameters.getBackend()));
  }

  @Test
  public void testWithoutRulesFullR8() throws Exception {
    // Running without rules is the same in full mode as for compat mode. The class is removed.
    testWithoutRules(testForR8(parameters.getBackend()));
  }

  // Tests for -keepclassmembers and *no* minification.

  private <
          C extends BaseCompilerCommand,
          B extends BaseCompilerCommand.Builder<C, B>,
          CR extends TestCompileResult<CR, RR>,
          RR extends TestRunResult<RR>,
          T extends TestShrinkerBuilder<C, B, CR, RR, T>>
      T buildWithMembersRule(TestShrinkerBuilder<C, B, CR, RR, T> builder) {
    return buildWithMembersRuleEnableMinification(builder).noMinification();
  }

  private <CR extends TestCompileResult<CR, RR>, RR extends TestRunResult<RR>>
      void assertMembersRuleCompatResult(CR compileResult) throws Exception {
    compileResult
        .inspect(
            inspector -> {
              assertTrue(inspector.clazz(MAIN_CLASS).isPresent());
              assertTrue(inspector.clazz(BAR_CLASS).isPresent());
              assertBarGetInstanceIsNotInlined(inspector);
              assertTrue(inspector.clazz(BAR_CLASS).uniqueFieldWithName("i").isPresent());
              assertTrue(inspector.clazz(BAR_CLASS).uniqueMethodWithName("<init>").isPresent());
            })
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testWithMembersRulePG() throws Exception {
    assertMembersRuleCompatResult(buildWithMembersRule(testForProguard(PG)).compile());
  }

  @Test
  public void testWithMembersRuleCompatR8() throws Exception {
    assertMembersRuleCompatResult(
        buildWithMembersRule(testForR8Compat(parameters.getBackend())).compile());
  }

  @Test
  public void testWithMembersRuleFullR8() throws Exception {
    // When a class is only referenced (not instantiated), full mode R8 will only keep the static
    // members specified by the -keepclassmembers rule.
    buildWithMembersRule(testForR8(parameters.getBackend()))
        .compile()
        .inspect(
            inspector -> {
              assertTrue(inspector.clazz(MAIN_CLASS).isPresent());
              assertTrue(inspector.clazz(BAR_CLASS).isPresent());
              assertBarGetInstanceIsNotInlined(inspector);
              assertThat(inspector.clazz(BAR_CLASS).init(), isPresent());
              assertThat(inspector.clazz(BAR_CLASS).uniqueFieldWithName("i"), isPresent());
            });
  }

  @Test
  public void testWithMembersRuleAndKeepBarRuleFullR8() throws Exception {
    // If we keep the Bar class too, we get the same behavior in full as in PG/Compat.
    assertMembersRuleCompatResult(
        buildWithMembersRule(testForR8(parameters.getBackend()))
            .addKeepClassRules(BAR_CLASS)
            .compile());
  }

  // Tests for non-static -keepclassmembers and *no* minification.

  private <
          C extends BaseCompilerCommand,
          B extends BaseCompilerCommand.Builder<C, B>,
          CR extends TestCompileResult<CR, RR>,
          RR extends TestRunResult<RR>,
          T extends TestShrinkerBuilder<C, B, CR, RR, T>>
      T buildWithNonStaticMembersRule(TestShrinkerBuilder<C, B, CR, RR, T> builder) {
    return builder
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MAIN_CLASS)
        .addKeepRules("-keepclassmembers " + KEEP_RULE_NON_STATIC)
        .noMinification();
  }

  @Test
  public void testWithNonStaticMembersRulePG() throws Exception {
    assertBarIsAbsent(buildWithNonStaticMembersRule(testForProguard(PG)).compile());
  }

  @Test
  public void testWithNonStaticMembersRuleCompatR8() throws Exception {
    assertBarIsAbsent(
        buildWithNonStaticMembersRule(testForR8Compat(parameters.getBackend())).compile());
  }

  @Test
  public void testWithNonStaticMembersRuleFullR8() throws Exception {
    assertBarIsAbsent(buildWithNonStaticMembersRule(testForR8(parameters.getBackend())).compile());
  }

  // Tests for -keepclassmembers and minification.

  private <
          C extends BaseCompilerCommand,
          B extends BaseCompilerCommand.Builder<C, B>,
          CR extends TestCompileResult<CR, RR>,
          RR extends TestRunResult<RR>,
          T extends TestShrinkerBuilder<C, B, CR, RR, T>>
      T buildWithMembersRuleEnableMinification(TestShrinkerBuilder<C, B, CR, RR, T> builder) {
    return builder
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MAIN_CLASS)
        .addKeepRules("-keepclassmembers " + KEEP_RULE);
  }

  private <CR extends TestCompileResult<CR, RR>, RR extends TestRunResult<RR>>
      void assertMembersRuleEnableMinificationCompatResult(CR compileResult) throws Exception {
    compileResult
        .inspect(
            inspector -> {
              assertTrue(inspector.clazz(MAIN_CLASS).isPresent());
              assertBarGetInstanceIsNotInlined(inspector);
              // Bar is renamed but its member names are kept.
              ClassSubject bar = inspector.clazz(BAR_CLASS);
              assertTrue(bar.isRenamed());
              FieldSubject barI = bar.uniqueFieldWithName("i");
              assertTrue(barI.isPresent());
              assertFalse(barI.isRenamed());
              MethodSubject barInit = bar.uniqueMethodWithName("<init>");
              assertTrue(barInit.isPresent());
              assertFalse(barInit.isRenamed());
              MethodSubject barInstance = bar.uniqueMethodWithName("instance");
              assertTrue(barInstance.isPresent());
              assertFalse(barInstance.isRenamed());
            })
        .run(parameters.getRuntime(), MAIN_CLASS)
        // Allowing minification will rename Bar, failing the reflective get.
        .assertFailureWithErrorThatMatches(
            allOf(
                containsString("ClassNotFoundException"), containsString(BAR_CLASS.getTypeName())));
  }

  @Test
  public void testWithMembersRuleEnableMinificationPG() throws Exception {
    assertMembersRuleEnableMinificationCompatResult(
        buildWithMembersRuleEnableMinification(testForProguard(PG)).compile());
  }

  @Test
  public void testWithMembersRuleEnableMinificationCompatR8() throws Exception {
    assertMembersRuleEnableMinificationCompatResult(
        buildWithMembersRuleEnableMinification(testForR8Compat(parameters.getBackend())).compile());
  }

  @Test
  public void testWithMembersRuleEnableMinificationFullR8() throws Exception {
    // When a class is only referenced (not instantiated), full mode R8 will only keep the static
    // members specified by the -keepclassmembers rule.
    buildWithMembersRuleEnableMinification(testForR8(parameters.getBackend()))
        .compile()
        .inspect(
            inspector -> {
              assertTrue(inspector.clazz(MAIN_CLASS).isPresent());
              assertTrue(inspector.clazz(BAR_CLASS).isPresent());
              assertBarGetInstanceIsNotInlined(inspector);
              assertThat(inspector.clazz(BAR_CLASS).uniqueMethodWithName("<init>"), isPresent());
              assertThat(inspector.clazz(BAR_CLASS).uniqueFieldWithName("i"), isPresent());
            });
  }

  // Tests for "-keepclassmembers class Bar", i.e, with no members specified.

  private <
          C extends BaseCompilerCommand,
          B extends BaseCompilerCommand.Builder<C, B>,
          CR extends TestCompileResult<CR, RR>,
          RR extends TestRunResult<RR>,
          T extends TestShrinkerBuilder<C, B, CR, RR, T>>
      void testWithMembersStarRule(TestShrinkerBuilder<C, B, CR, RR, T> builder) throws Exception {
    assertBarIsAbsent(
        builder
            .addProgramClasses(CLASSES)
            .addKeepMainRule(MAIN_CLASS)
            .noMinification()
            .addKeepRules("-keepclassmembers class " + Bar.class.getTypeName())
            .compile());
  }

  @Test
  public void testWithMembersStarRulePG() throws Exception {
    testWithMembersStarRule(testForProguard(PG));
  }

  @Test
  public void testWithMembersStarRuleCompatR8() throws Exception {
    testWithMembersStarRule(testForR8Compat(parameters.getBackend()));
  }

  @Test
  public void testWithMembersStarRuleFullR8() throws Exception {
    testWithMembersStarRule(testForR8(parameters.getBackend()));
  }

  // Tests for "-keepclassmembernames" and *no* minification.

  private <
          C extends BaseCompilerCommand,
          B extends BaseCompilerCommand.Builder<C, B>,
          CR extends TestCompileResult<CR, RR>,
          RR extends TestRunResult<RR>,
          T extends TestShrinkerBuilder<C, B, CR, RR, T>>
      T buildWithMemberNamesRule(TestShrinkerBuilder<C, B, CR, RR, T> builder) {
    return buildWithMemberNamesRuleEnableMinification(builder).noMinification();
  }

  private <CR extends TestCompileResult<CR, RR>, RR extends TestRunResult<RR>>
      void assertMemberNamesRuleCompatResult(CR compileResult) throws Exception {
    compileResult
        .inspect(
            inspector -> {
              assertTrue(inspector.clazz(MAIN_CLASS).isPresent());
              assertBarGetInstanceIsNotInlined(inspector);
              ClassSubject bar = inspector.clazz(BAR_CLASS);
              assertTrue(bar.isPresent());
              assertTrue(bar.uniqueMethodWithName("instance").isPresent());
              // Reflected on fields are not kept.
              assertFalse(bar.uniqueMethodWithName("<init>").isPresent());
              assertFalse(bar.uniqueFieldWithName("i").isPresent());
            })
        .run(parameters.getRuntime(), MAIN_CLASS)
        // Keeping the instance and its accessed members does not keep the reflected <init> and i.
        .assertFailureWithErrorThatMatches(
            allOf(
                containsString("NoSuchMethodException"),
                containsString(BAR_CLASS.getTypeName() + ".<init>()")));
  }

  @Test
  public void testWithMemberNamesRulePG() throws Exception {
    assertMemberNamesRuleCompatResult(buildWithMemberNamesRule(testForProguard(PG)).compile());
  }

  @Test
  public void testWithMemberNamesRuleCompatR8() throws Exception {
    assertMemberNamesRuleCompatResult(
        buildWithMemberNamesRule(testForR8Compat(parameters.getBackend()))
            .enableInliningAnnotations()
            .compile());
  }

  @Test
  public void testWithMemberNamesRuleFullR8() throws Exception {
    assertMemberNamesRuleCompatResult(
        buildWithMemberNamesRule(testForR8(parameters.getBackend())).compile());
  }

  // Tests for "-keepclassmembernames" and *no* minification.

  private <
          C extends BaseCompilerCommand,
          B extends BaseCompilerCommand.Builder<C, B>,
          CR extends TestCompileResult<CR, RR>,
          RR extends TestRunResult<RR>,
          T extends TestShrinkerBuilder<C, B, CR, RR, T>>
      T buildWithMemberNamesRuleEnableMinification(TestShrinkerBuilder<C, B, CR, RR, T> builder) {
    return builder
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MAIN_CLASS)
        .addKeepRules("-keepclassmembernames " + KEEP_RULE);
  }

  private <CR extends TestCompileResult<CR, RR>, RR extends TestRunResult<RR>>
      void assertMemberNamesRuleEnableMinificationCompatResult(CR compileResult) throws Exception {
    compileResult
        .inspect(
            inspector -> {
              assertTrue(inspector.clazz(MAIN_CLASS).isPresent());
              assertBarGetInstanceIsNotInlined(inspector);
              ClassSubject bar = inspector.clazz(BAR_CLASS);
              assertTrue(bar.isPresent());
              assertTrue(bar.isRenamed());
              assertFalse(bar.uniqueFieldWithName("i").isPresent());
              assertFalse(bar.uniqueMethodWithName("<init>").isPresent());
              MethodSubject barInstance = bar.uniqueMethodWithName("instance");
              assertTrue(barInstance.isPresent());
              assertFalse(barInstance.isRenamed());
            })
        .run(parameters.getRuntime(), MAIN_CLASS)
        // Allowing minification will rename Bar, failing the reflective get.
        .assertFailureWithErrorThatMatches(
            allOf(
                containsString("ClassNotFoundException"), containsString(BAR_CLASS.getTypeName())));
  }

  @Test
  public void testWithMemberNamesRuleEnableMinificationPG() throws Exception {
    assertMemberNamesRuleEnableMinificationCompatResult(
        buildWithMemberNamesRuleEnableMinification(testForProguard(PG)).compile());
  }

  @Test
  public void testWithMemberNamesRuleEnableMinificationCompatR8() throws Exception {
    assertMemberNamesRuleEnableMinificationCompatResult(
        buildWithMemberNamesRuleEnableMinification(testForR8Compat(parameters.getBackend()))
            .enableInliningAnnotations()
            .compile());
  }

  @Test
  public void testWithMemberNamesRuleEnableMinificationFullR8() throws Exception {
    assertMemberNamesRuleEnableMinificationCompatResult(
        buildWithMemberNamesRuleEnableMinification(testForR8(parameters.getBackend())).compile());
  }
}
