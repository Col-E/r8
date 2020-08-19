// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.attributes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isMemberClass;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.shaking.attributes.testclasses.Outer;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class Main {

  public static void main(String[] args) {
    Class<?>[] declaredClasses = Outer.class.getDeclaredClasses();
    if (declaredClasses.length == 0) {
      System.out.println("No declared classes");
    } else {
      for (int i = 0; i < declaredClasses.length; i++) {
        System.out.println("Declared class: " + declaredClasses[i].getName());
      }
    }
    if (Outer.Inner.class.getDeclaringClass() == null) {
      System.out.println("No declaring classes");
    } else {
      System.out.println("Declaring class: " + Outer.Inner.class.getDeclaringClass().getName());
    }
  }
}

@RunWith(Parameterized.class)
public class KeepInnerClassesEnclosingMethodAnnotationsTest extends TestBase {

  enum TestConfig {
    R8,
    PROGUARD
  }

  @Parameterized.Parameters(name = "{0}")
  public static Object[] parameters() {
    return TestConfig.values();
  }

  private final TestConfig config;

  public KeepInnerClassesEnclosingMethodAnnotationsTest(TestConfig config) {
    this.config = config;
  }

  private static class TestResult {

    final String stdout;
    final CodeInspector inspector;
    final ClassSubject outer;
    final ClassSubject inner;

    TestResult(SingleTestRunResult<?> result) throws Throwable {
      this.stdout = result.getStdOut();
      this.inspector = result.inspector();
      this.outer = inspector.clazz(Outer.class);
      this.inner = inspector.clazz(Outer.Inner.class);
      assertThat(outer, isPresent());
      assertThat(inner, isPresent());
    }
  }

  private TestResult runProguard(List<String> proguardConfiguration) throws Throwable {
    return new TestResult(
        testForProguard()
            .addProgramClasses(ImmutableList.of(Main.class, Outer.class, Outer.Inner.class))
            .addKeepRules(proguardConfiguration)
            .run(Main.class));
  }

  private TestResult runR8(List<String> proguardConfiguration) throws Throwable {
    return new TestResult(
        testForR8(Backend.DEX)
            .addProgramClassesAndInnerClasses(Outer.class)
            .addProgramClasses(Main.class)
            .addKeepRules(proguardConfiguration)
            .run(Main.class));
  }

  private TestResult runShrinker(List<String> proguardConfiguration) throws Throwable {
    return config == TestConfig.R8
        ? runR8(proguardConfiguration)
        : runProguard(proguardConfiguration);
  }

  private void noInnerClassesEnclosingMethodInformation(TestResult result) {
    List<String> lines = StringUtils.splitLines(result.stdout);
    assertEquals(2, lines.size());
    assertEquals("No declared classes", lines.get(0));
    assertEquals("No declaring classes", lines.get(1));
  }

  private void fullInnerClassesEnclosingMethodInformation(TestResult result) {
    List<String> lines = StringUtils.splitLines(result.stdout);
    assertEquals(2, lines.size());
    assertEquals("Declared class: " + result.inner.getFinalName(), lines.get(0));
    assertEquals("Declaring class: " + result.outer.getFinalName(), lines.get(1));
  }

  @Test
  public void testKeepAll() throws Throwable {
    TestResult result =
        runShrinker(
            ImmutableList.of(
                "-keepattributes InnerClasses,EnclosingMethod",
                "-keep class **Outer*",
                keepMainProguardConfiguration(Main.class)));
    assertThat(result.outer, isPresentAndNotRenamed());
    assertThat(result.inner, isPresentAndNotRenamed());
    assertThat(result.inner, isMemberClass());
    fullInnerClassesEnclosingMethodInformation(result);
  }

  @Test
  public void testKeepAllWithoutAttributes() throws Throwable {
    TestResult result =
        runShrinker(
            ImmutableList.of("-keep class **Outer*", keepMainProguardConfiguration(Main.class)));
    assertThat(result.outer, isPresentAndNotRenamed());
    assertThat(result.inner, isPresentAndNotRenamed());
    assertThat(result.inner, not(isMemberClass()));
    noInnerClassesEnclosingMethodInformation(result);
  }

  @Test
  public void testKeepInner() throws Throwable {
    TestResult result =
        runShrinker(
            ImmutableList.of(
                "-keepattributes InnerClasses,EnclosingMethod",
                "-keep class **Outer$Inner",
                keepMainProguardConfiguration(Main.class)));
    assertThat(result.outer, isPresentAndNotRenamed());
    assertThat(result.inner, isPresentAndNotRenamed());
    assertThat(result.inner, isMemberClass());
    fullInnerClassesEnclosingMethodInformation(result);
  }

  @Test
  public void testKeepOuter() throws Throwable {
    TestResult result =
        runShrinker(
            ImmutableList.of(
                "-keepattributes InnerClasses,EnclosingMethod",
                "-keep class **Outer",
                keepMainProguardConfiguration(Main.class)));
    assertThat(result.outer, isPresentAndNotRenamed());
    assertThat(result.inner, isPresentAndRenamed());
    assertThat(result.inner, isMemberClass());
    fullInnerClassesEnclosingMethodInformation(result);
  }
}
