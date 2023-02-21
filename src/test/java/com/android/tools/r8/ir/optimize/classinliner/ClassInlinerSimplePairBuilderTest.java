// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.AlwaysClassInline;
import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ClassInlinerSimplePairBuilderTest extends ClassInlinerTestBase {

  private static final String EXPECTED =
      StringUtils.lines(
          "[before] first = null",
          "[after] first = f1",
          "Pair(f1, <null>)",
          "[before] second = null",
          "[after] second = s2",
          "Pair(<null>, s2)",
          "[before] first = null",
          "[after] first = f3",
          "[before] second = null",
          "[after] second = s4",
          "Pair(f3, s4)");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInlinerSimplePairBuilderTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassInlinerSimplePairBuilderTest.class)
        .addKeepMainRule(TestClass.class)
        .enableAlwaysClassInlineAnnotations()
        .enableAlwaysInliningAnnotations()
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testExpectedBehavior() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(ClassInlinerSimplePairBuilderTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(TestClass.class);
    assertThat(inspector.clazz(PairBuilder.class), not(isPresent()));

    Set<String> expected = ImmutableSet.of(StringBuilder.class.getTypeName());
    assertEquals(expected, collectTypes(clazz.uniqueMethodWithOriginalName("testSimpleBuilder1")));
    assertEquals(expected, collectTypes(clazz.uniqueMethodWithOriginalName("testSimpleBuilder2")));
    assertEquals(expected, collectTypes(clazz.uniqueMethodWithOriginalName("testSimpleBuilder3")));
  }

  static class TestClass {

    public static void main(String[] args) {
      testSimpleBuilder1();
      testSimpleBuilder2();
      testSimpleBuilder3();
    }

    @NeverInline
    static void testSimpleBuilder1() {
      System.out.println(new PairBuilder<String, String>().setFirst("f1").build().myToString());
    }

    @NeverInline
    static void testSimpleBuilder2() {
      System.out.println(new PairBuilder<String, String>().setSecond("s2").build().myToString());
    }

    @NeverInline
    static void testSimpleBuilder3() {
      System.out.println(
          new PairBuilder<String, String>().setFirst("f3").setSecond("s4").build().myToString());
    }
  }

  static class Pair<F, S> {
    final F first;
    final S second;

    Pair(F first, S second) {
      this.first = first;
      this.second = second;
    }

    String myToString() {
      return "Pair("
          + (first == null ? "<null>" : first)
          + ", "
          + (second == null ? "<null>" : second)
          + ")";
    }
  }

  @AlwaysClassInline
  @NoHorizontalClassMerging
  static class PairBuilder<F, S> {

    F first;
    S second = null;

    PairBuilder<F, S> setFirst(F first) {
      System.out.println("[before] first = " + this.first);
      this.first = first;
      System.out.println("[after] first = " + this.first);
      return this;
    }

    PairBuilder<F, S> setSecond(S second) {
      System.out.println("[before] second = " + this.second);
      this.second = second;
      System.out.println("[after] second = " + this.second);
      return this;
    }

    @AlwaysInline
    public Pair<F, S> build() {
      return new Pair<>(first, second);
    }
  }
}
