// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.proguard.configuration;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;

public class InheritanceClauseWithDisjunctionTest extends TestBase {

  @Ignore("b/128503974")
  @Test
  public void testExtendsClauseWithR8() throws Exception {
    runTest(
        testForR8(Backend.DEX),
        getKeepRulesForExtendsClauseTests(),
        InheritanceClauseWithDisjunctionTest::inspectExtendsClauseTests);
  }

  @Test
  public void testExtendsClauseWithProguard() throws Exception {
    runTest(
        testForProguard(),
        getKeepRulesForExtendsClauseTests(),
        InheritanceClauseWithDisjunctionTest::inspectExtendsClauseTests);
  }

  private static List<String> getKeepRulesForExtendsClauseTests() {
    return ImmutableList.of(
        "-keep class * extends "
            + InheritanceClauseWithDisjunctionTestClassA.class.getTypeName()
            + ", "
            + InheritanceClauseWithDisjunctionTestClassB.class.getTypeName()
            + ", "
            + InheritanceClauseWithDisjunctionTestClassC.class.getTypeName());
  }

  private static void inspectExtendsClauseTests(CodeInspector inspector) {
    // ASub extends A and BSub extends B. A and B are kept because Proguard presumably does not
    // merge them into ASub and BSub, respectively.
    assertEquals(4, inspector.allClasses().size());
    assertThat(inspector.clazz(InheritanceClauseWithDisjunctionTestClassA.class), isPresent());
    assertThat(inspector.clazz(InheritanceClauseWithDisjunctionTestClassASub.class), isPresent());
    assertThat(inspector.clazz(InheritanceClauseWithDisjunctionTestClassB.class), isPresent());
    assertThat(inspector.clazz(InheritanceClauseWithDisjunctionTestClassBSub.class), isPresent());
  }

  @Ignore("b/128503974")
  @Test
  public void testExtendsClauseWithNegation1WithR8() throws Exception {
    runTest(
        testForR8(Backend.DEX),
        getKeepRulesForExtendsClauseWithNegation1Tests(),
        InheritanceClauseWithDisjunctionTest::inspectExtendsClauseWithNegation1Tests);
  }

  @Test
  public void testExtendsClauseWithNegation1WithProguard() throws Exception {
    runTest(
        testForProguard(),
        getKeepRulesForExtendsClauseWithNegation1Tests(),
        InheritanceClauseWithDisjunctionTest::inspectExtendsClauseWithNegation1Tests);
  }

  private static List<String> getKeepRulesForExtendsClauseWithNegation1Tests() {
    return ImmutableList.of(
        "-keep class * extends "
            + InheritanceClauseWithDisjunctionTestClassA.class.getTypeName()
            + ", !"
            + InheritanceClauseWithDisjunctionTestClassB.class.getTypeName());
  }

  private static void inspectExtendsClauseWithNegation1Tests(CodeInspector inspector) {
    // Strangely, BSub is kept, although it does not extend A and it extends B.
    assertEquals(11, inspector.allClasses().size());
  }

  @Ignore("b/128503974")
  @Test
  public void testExtendsClauseWithNegation2WithR8() throws Exception {
    runTest(
        testForR8(Backend.DEX),
        getKeepRulesForExtendsClauseWithNegation2Tests(),
        InheritanceClauseWithDisjunctionTest::inspectExtendsClauseWithNegation2Tests);
  }

  @Test
  public void testExtendsClauseWithNegation2WithProguard() throws Exception {
    runTest(
        testForProguard(),
        getKeepRulesForExtendsClauseWithNegation2Tests(),
        InheritanceClauseWithDisjunctionTest::inspectExtendsClauseWithNegation2Tests);
  }

  private static List<String> getKeepRulesForExtendsClauseWithNegation2Tests() {
    return ImmutableList.of(
        "-keep class * extends !"
            + InheritanceClauseWithDisjunctionTestClassB.class.getTypeName()
            + ", "
            + InheritanceClauseWithDisjunctionTestClassA.class.getTypeName());
  }

  private static void inspectExtendsClauseWithNegation2Tests(CodeInspector inspector) {
    // Strangely, all the types that do not extend A have not kept.
    assertEquals(2, inspector.allClasses().size());
    assertThat(inspector.clazz(InheritanceClauseWithDisjunctionTestClassA.class), isPresent());
    assertThat(inspector.clazz(InheritanceClauseWithDisjunctionTestClassASub.class), isPresent());
  }

  @Ignore("b/128503974")
  @Test
  public void testExtendsClauseWithMatchAllNegationWithR8() throws Exception {
    runTest(
        testForR8(Backend.DEX),
        getKeepRulesForExtendsClauseWithMatchAllNegationTests(),
        InheritanceClauseWithDisjunctionTest::inspectExtendsClauseWithMatchAllNegationTests);
  }

  @Test
  public void testExtendsClauseWithMatchAllNegationWithProguard() throws Exception {
    runTest(
        testForProguard(),
        getKeepRulesForExtendsClauseWithMatchAllNegationTests(),
        InheritanceClauseWithDisjunctionTest::inspectExtendsClauseWithMatchAllNegationTests);
  }

  private static List<String> getKeepRulesForExtendsClauseWithMatchAllNegationTests() {
    return ImmutableList.of(
        "-keep class * extends "
            + InheritanceClauseWithDisjunctionTestClassA.class.getTypeName()
            + ", !"
            + InheritanceClauseWithDisjunctionTestClassA.class.getTypeName());
  }

  private static void inspectExtendsClauseWithMatchAllNegationTests(CodeInspector inspector) {
    // Every type extends A or does not extend A.
    assertEquals(11, inspector.allClasses().size());
  }

  @Ignore("b/128503974")
  @Test
  public void testImplementsClauseWithR8() throws Exception {
    runTest(
        testForR8(Backend.DEX),
        getKeepRulesForImplementsClauseTests(),
        InheritanceClauseWithDisjunctionTest::inspectImplementsClauseTests);
  }

  @Test
  public void testImplementsClauseWithProguard() throws Exception {
    try {
      runTest(
          testForProguard(),
          getKeepRulesForImplementsClauseTests(),
          InheritanceClauseWithDisjunctionTest::inspectImplementsClauseTests);
      fail();
    } catch (CompilationFailedException e) {
      // Strangely, nothing matches implements I or J (not even InheritanceClauseWithDisjunction-
      // TestClassIJSub, which implements both I and J!).
      assertThat(e.getMessage(), containsString("The output jar is empty"));
    }
  }

  private static List<String> getKeepRulesForImplementsClauseTests() {
    return ImmutableList.of(
        "-keep class * implements "
            + InheritanceClauseWithDisjunctionTestInterfaceI.class.getTypeName()
            + ", "
            + InheritanceClauseWithDisjunctionTestInterfaceJ.class.getTypeName()
            + ", "
            + InheritanceClauseWithDisjunctionTestInterfaceK.class.getTypeName());
  }

  private static void inspectImplementsClauseTests(CodeInspector inspector) {
    // Strangely, nothing matches implements I or J (not even InheritanceClauseWithDisjunctionTest-
    // ClassIJSub, which implements both I and J!).
    assertEquals(0, inspector.allClasses().size());
  }

  @Ignore("b/128503974")
  @Test
  public void testImplementsClauseWithNegationWithR8() throws Exception {
    runTest(
        testForR8(Backend.DEX),
        getKeepRulesForImplementsClauseTests(),
        InheritanceClauseWithDisjunctionTest::inspectImplementsClauseWithNegationTests);
  }

  @Test
  public void testImplementsClauseWithNegationWithProguard() throws Exception {
    runTest(
        testForProguard(),
        getKeepRulesForImplementsClauseWithNegationTests(),
        InheritanceClauseWithDisjunctionTest::inspectImplementsClauseWithNegationTests);
  }

  private static List<String> getKeepRulesForImplementsClauseWithNegationTests() {
    return ImmutableList.of(
        "-keep class * implements "
            + InheritanceClauseWithDisjunctionTestInterfaceI.class.getTypeName()
            + ", !"
            + InheritanceClauseWithDisjunctionTestInterfaceI.class.getTypeName());
  }

  private static void inspectImplementsClauseWithNegationTests(CodeInspector inspector) {
    // Every type implements I or does not implement I.
    assertEquals(11, inspector.allClasses().size());
  }

  private static void runTest(
      TestShrinkerBuilder<?, ?, ?, ?, ?> builder,
      List<String> keepRules,
      ThrowingConsumer<CodeInspector, RuntimeException> consumer)
      throws Exception {
    builder
        .addProgramClasses(
            InheritanceClauseWithDisjunctionTestClassA.class,
            InheritanceClauseWithDisjunctionTestClassASub.class,
            InheritanceClauseWithDisjunctionTestClassB.class,
            InheritanceClauseWithDisjunctionTestClassBSub.class,
            InheritanceClauseWithDisjunctionTestClassC.class,
            InheritanceClauseWithDisjunctionTestInterfaceI.class,
            InheritanceClauseWithDisjunctionTestClassISub.class,
            InheritanceClauseWithDisjunctionTestInterfaceJ.class,
            InheritanceClauseWithDisjunctionTestClassJSub.class,
            InheritanceClauseWithDisjunctionTestInterfaceK.class,
            InheritanceClauseWithDisjunctionTestClassIJKSub.class)
        .addKeepRules(keepRules)
        .compile()
        .inspect(consumer);
  }
}

class InheritanceClauseWithDisjunctionTestClassA {}

class InheritanceClauseWithDisjunctionTestClassASub
    extends InheritanceClauseWithDisjunctionTestClassA {}

class InheritanceClauseWithDisjunctionTestClassB {}

class InheritanceClauseWithDisjunctionTestClassBSub
    extends InheritanceClauseWithDisjunctionTestClassB {}

class InheritanceClauseWithDisjunctionTestClassC {}

interface InheritanceClauseWithDisjunctionTestInterfaceI {}

class InheritanceClauseWithDisjunctionTestClassISub
    implements InheritanceClauseWithDisjunctionTestInterfaceI {}

interface InheritanceClauseWithDisjunctionTestInterfaceJ {}

class InheritanceClauseWithDisjunctionTestClassJSub
    implements InheritanceClauseWithDisjunctionTestInterfaceJ {}

interface InheritanceClauseWithDisjunctionTestInterfaceK {}

class InheritanceClauseWithDisjunctionTestClassIJKSub
    implements InheritanceClauseWithDisjunctionTestInterfaceI,
        InheritanceClauseWithDisjunctionTestInterfaceJ,
        InheritanceClauseWithDisjunctionTestInterfaceK {}
