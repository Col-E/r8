// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ConditionalKeepIfKeptTest extends TestBase {

  static final String EXPECTED =
      StringUtils.lines("class " + StaticallyReferenced.class.getTypeName());

  private final TestParameters parameters;
  private final boolean useMarker;

  @Parameterized.Parameters(name = "{0}, marker:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().withDexRuntimes().withAllApiLevels().build(),
        BooleanUtils.values());
  }

  public ConditionalKeepIfKeptTest(TestParameters parameters, boolean useMarker) {
    this.parameters = parameters;
    this.useMarker = useMarker;
  }

  private String getConditionalRulePrefix() {
    String clazz = StaticallyReferenced.class.getTypeName();
    return useMarker
        ? "-if @" + Marked.class.getTypeName() + " class * -keep class <1>"
        : "-if class " + clazz + " -keep class " + clazz;
  }

  @Test
  public void testIfKeepNoMembers() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ConditionalKeepIfKeptTest.class)
        .addKeepRules(getConditionalRulePrefix())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(StaticallyReferenced.class);
              assertThat(classSubject, isPresent());
              assertEquals(0, classSubject.allFields().size());
              // TODO(b/132318799): Should not keep <init>() when not specified.
              assertEquals(1, classSubject.allMethods().size());
              assertTrue(classSubject.init().isPresent());
            });
  }

  @Test
  public void testIfKeepAllMembers() throws Exception {
    Assume.assumeFalse("b/132318609", useMarker);
    testForR8(parameters.getBackend())
        .addInnerClasses(ConditionalKeepIfKeptTest.class)
        .addKeepRules(getConditionalRulePrefix() + " { *; }")
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(StaticallyReferenced.class);
              assertThat(classSubject, isPresent());
              assertEquals(2, classSubject.allFields().size());
              assertEquals(3, classSubject.allMethods().size());
            });
  }

  @Test
  public void testIfKeepStaticMembers() throws Exception {
    Assume.assumeFalse("b/132318609", useMarker);
    testForR8(parameters.getBackend())
        .addInnerClasses(ConditionalKeepIfKeptTest.class)
        .addKeepRules(getConditionalRulePrefix() + " { static *; }")
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(StaticallyReferenced.class);
              assertThat(classSubject, isPresent());
              assertEquals(1, classSubject.allFields().size());
              assertEquals(1, classSubject.allMethods().size());
            });
  }

  @Test
  public void testIfKeepNonStaticMembers() throws Exception {
    Assume.assumeFalse("b/132318609", useMarker);
    testForR8(parameters.getBackend())
        .addInnerClasses(ConditionalKeepIfKeptTest.class)
        .addKeepRules(getConditionalRulePrefix() + " { !static *; }")
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(StaticallyReferenced.class);
              assertThat(classSubject, isPresent());
              assertEquals(1, classSubject.allFields().size());
              assertEquals(2, classSubject.allMethods().size());
            });
  }

  @Test
  public void testIfKeepFields() throws Exception {
    Assume.assumeFalse("b/132318609", useMarker);
    testForR8(parameters.getBackend())
        .addInnerClasses(ConditionalKeepIfKeptTest.class)
        .addKeepRules(getConditionalRulePrefix() + " { <fields>; }")
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(StaticallyReferenced.class);
              assertThat(classSubject, isPresent());
              assertEquals(2, classSubject.allFields().size());
              assertEquals(0, classSubject.allMethods().size());
            });
  }

  @Test
  public void testIfKeepMethods() throws Exception {
    Assume.assumeFalse("b/132318609", useMarker);
    testForR8(parameters.getBackend())
        .addInnerClasses(ConditionalKeepIfKeptTest.class)
        .addKeepRules(getConditionalRulePrefix() + " { <methods>; }")
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(StaticallyReferenced.class);
              assertThat(classSubject, isPresent());
              assertEquals(3, classSubject.allMethods().size());
              classSubject.allMethods().forEach(m -> assertThat(m, isPresentAndNotRenamed()));
              // Keeping methods will cause the fields to be kept too (but allow renaming them).
              assertEquals(2, classSubject.allFields().size());
              classSubject.allFields().forEach(f -> assertThat(f, isPresentAndRenamed()));
            });
  }

  @interface Marked {}

  @Marked
  static class StaticallyReferenced {
    static long staticField;

    static long staticMethod() {
      return staticField += System.nanoTime();
    }

    long nonStaticField;

    StaticallyReferenced() {
      nonStaticField = System.nanoTime();
    }

    long nonStaticMethod() {
      return nonStaticField;
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(StaticallyReferenced.class);
    }
  }
}
