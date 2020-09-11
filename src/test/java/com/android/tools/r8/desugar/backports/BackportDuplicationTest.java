// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.backports;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.desugar.backports.AbstractBackportTest.MiniAssert;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BackportDuplicationTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  static final List<Class<?>> CLASSES =
      ImmutableList.of(MiniAssert.class, TestClass.class, User1.class, User2.class);

  static final List<String> CLASS_TYPE_NAMES =
      CLASSES.stream().map(Class::getTypeName).collect(Collectors.toList());

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withApiLevel(AndroidApiLevel.J).build();
  }

  public BackportDuplicationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    runR8(false);
    runR8(true);
  }

  private void runR8(boolean minify) throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(MiniAssert.class)
        .setMinApi(parameters.getApiLevel())
        .minification(minify)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkNoInternalSyntheticNames);
  }

  @Test
  public void testD8() throws Exception {
    List<String> run1 = getClassesAfterD8CompileAndRun();
    List<String> run2 = getClassesAfterD8CompileAndRun();
    assertEquals("Non deterministic synthesis", run1, run2);
  }

  private List<String> getClassesAfterD8CompileAndRun() throws Exception {
    return testForD8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkNoInternalSyntheticNames)
        .inspect(this::checkExpectedOutput)
        .inspector()
        .allClasses()
        .stream()
        .filter(c -> !CLASS_TYPE_NAMES.contains(c.getFinalName()))
        .flatMap(c -> c.allMethods().stream().map(m -> m.asMethodReference().toString()))
        .sorted()
        .collect(Collectors.toList());
  }

  private void checkNoInternalSyntheticNames(CodeInspector inspector) {
    inspector.forAllClasses(
        clazz -> {
          assertThat(
              clazz.getFinalName(),
              not(containsString(SyntheticItems.INTERNAL_SYNTHETIC_CLASS_SEPARATOR)));
        });
  }

  private void checkExpectedOutput(CodeInspector inspector) {
    // TODO(b/158159959): Once synthetic methods can be grouped in classes this should become 1.
    int expectedSynthesizedClasses = 3;
    // Total number of synthetic methods should be 3 ({Boolean,Character,Long}.compare).
    int expectedSynthesizedMethods = 3;
    // Desugaring should add exactly one class with one desugared method.
    assertEquals(expectedSynthesizedClasses, inspector.allClasses().size() - CLASSES.size());
    assertThat(
        inspector.allClasses().stream()
            .map(ClassSubject::getOriginalName)
            .collect(Collectors.toList()),
        hasItems(CLASS_TYPE_NAMES.toArray()));
    List<FoundMethodSubject> methods =
        inspector.allClasses().stream()
            .filter(clazz -> !CLASS_TYPE_NAMES.contains(clazz.getOriginalName()))
            .flatMap(clazz -> clazz.allMethods().stream())
            .collect(Collectors.toList());
    assertEquals(expectedSynthesizedMethods, methods.size());
  }

  static class User1 {

    private static void testBooleanCompare() {
      // These 4 calls should share the same synthetic method.
      MiniAssert.assertTrue(Boolean.compare(true, false) > 0);
      MiniAssert.assertTrue(Boolean.compare(true, true) == 0);
      MiniAssert.assertTrue(Boolean.compare(false, false) == 0);
      MiniAssert.assertTrue(Boolean.compare(false, true) < 0);
    }

    private static void testCharacterCompare() {
      // All 6 (User1 and User2) calls should share the same synthetic method.
      MiniAssert.assertTrue(Character.compare('b', 'a') > 0);
      MiniAssert.assertTrue(Character.compare('a', 'a') == 0);
      MiniAssert.assertTrue(Character.compare('a', 'b') < 0);
    }
  }

  static class User2 {

    private static void testCharacterCompare() {
      // All 6 (User1 and User2) calls should share the same synthetic method.
      MiniAssert.assertTrue(Character.compare('y', 'x') > 0);
      MiniAssert.assertTrue(Character.compare('x', 'x') == 0);
      MiniAssert.assertTrue(Character.compare('x', 'y') < 0);
    }

    private static void testIntegerCompare() {
      // These 3 calls should share the same synthetic method.
      MiniAssert.assertTrue(Integer.compare(2, 0) > 0);
      MiniAssert.assertTrue(Integer.compare(0, 0) == 0);
      MiniAssert.assertTrue(Integer.compare(0, 2) < 0);
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      User1.testBooleanCompare();
      User1.testCharacterCompare();
      User2.testCharacterCompare();
      User2.testIntegerCompare();
      System.out.println("Hello, world");
    }
  }
}
