// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compatproguard;

import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
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

  private static Class<?> MAIN_CLASS = CompatKeepClassMemberNamesTest.class;
  private static Class<?> BAR_CLASS = CompatKeepClassMemberNamesTest.Bar.class;
  private static Collection<Class<?>> CLASSES = ImmutableList.of(MAIN_CLASS, BAR_CLASS);

  private static String EXPLICIT_RULE =
      "class "
          + Bar.class.getTypeName()
          + " { static "
          + Bar.class.getTypeName()
          + " instance(); void <init>(); int i; }";

  private static String EXPECTED = StringUtils.lines("42", "null");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  private final TestParameters parameters;

  public CompatKeepClassMemberNamesTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    testForJvm()
        .addProgramClasses(CLASSES)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testWithoutRulesPG() throws Exception {
    testForProguard()
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MAIN_CLASS)
        .noMinification()
        .compile()
        .inspect(
            inspector -> {
              assertTrue(inspector.clazz(MAIN_CLASS).isPresent());
              // Bar.instance() is inlined away allowing Bar to be removed fully.
              assertFalse(inspector.clazz(BAR_CLASS).isPresent());
            })
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertFailureWithErrorThatMatches(
            allOf(
                containsString("ClassNotFoundException"), containsString(BAR_CLASS.getTypeName())));
  }

  @Test
  public void testWithMembersRulePG() throws Exception {
    testForProguard()
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MAIN_CLASS)
        .noMinification()
        .addKeepRules("-keepclassmembers " + EXPLICIT_RULE)
        .compile()
        .inspect(
            inspector -> {
              assertTrue(inspector.clazz(MAIN_CLASS).isPresent());
              assertTrue(inspector.clazz(BAR_CLASS).isPresent());
              assertBarGetInstanceIsNotInlined(inspector);
              assertTrue(inspector.clazz(BAR_CLASS).uniqueFieldWithName("i").isPresent());
            })
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testWithMembersRuleAllowRenamingPG() throws Exception {
    testForProguard()
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MAIN_CLASS)
        .addKeepRules("-keepclassmembers " + EXPLICIT_RULE)
        .compile()
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
  public void testWithMembersStarRulePG() throws Exception {
    testForProguard()
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MAIN_CLASS)
        .noMinification()
        .addKeepRules("-keepclassmembers class " + Bar.class.getTypeName())
        .compile()
        .inspect(
            inspector -> {
              assertTrue(inspector.clazz(MAIN_CLASS).isPresent());
              // A rule without body does not imply { *; }, thus Bar is removed.
              assertFalse(inspector.clazz(BAR_CLASS).isPresent());
            })
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertFailureWithErrorThatMatches(
            allOf(
                containsString("ClassNotFoundException"), containsString(BAR_CLASS.getTypeName())));
  }

  @Test
  public void testWithMemberNamesRulePG() throws Exception {
    testForProguard()
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MAIN_CLASS)
        .noMinification()
        .addKeepRules("-keepclassmembernames " + EXPLICIT_RULE)
        .compile()
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
  public void testWithMemberNamesRuleAllowRenamingPG() throws Exception {
    testForProguard()
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MAIN_CLASS)
        .addKeepRules("-keepclassmembernames " + EXPLICIT_RULE)
        .compile()
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

  private static void assertBarGetInstanceIsNotInlined(CodeInspector inspector) {
    assertTrue(
        inspector
            .clazz(MAIN_CLASS)
            .uniqueMethodWithName("main")
            .streamInstructions()
            .anyMatch(i -> i.isInvoke() && i.getMethod().qualifiedName().contains("instance")));
  }
}
