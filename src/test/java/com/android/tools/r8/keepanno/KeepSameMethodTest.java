// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.KeepBinding;
import com.android.tools.r8.keepanno.annotations.KeepCondition;
import com.android.tools.r8.keepanno.annotations.KeepEdge;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepSameMethodTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("foo");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public KeepSameMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getInputClasses())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testWithRuleExtraction() throws Exception {
    testForR8(parameters.getBackend())
        .enableExperimentalKeepAnnotations()
        .addProgramClasses(getInputClasses())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        // The "all members" target will create an unused "all fields" rule.
        .allowUnusedProguardConfigurationRules()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkOutput);
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, A.class);
  }

  private void checkOutput(CodeInspector inspector) throws Exception {
    assertThat(inspector.clazz(A.class).method(A.class.getMethod("foo")), isPresent());
    // TODO(b/265892343): The extracted rule will match all params so this is incorrectly kept.
    assertThat(inspector.clazz(A.class).method(A.class.getMethod("foo", int.class)), isPresent());
    // Bar is unused and thus removed.
    assertThat(inspector.clazz(A.class).uniqueMethodWithOriginalName("bar"), isAbsent());
  }

  /**
   * This conditional rule expresses that if any class in the program has a live member then that
   * same member is to be kept (soft-pinned, e.g., no inlining, no renaming of the member, etc.).
   */
  @KeepEdge(
      bindings = {
        @KeepBinding(
            bindingName = "AnyMemberOnA",
            kind = KeepItemKind.ONLY_MEMBERS,
            classConstant = A.class)
      },
      preconditions = {@KeepCondition(memberFromBinding = "AnyMemberOnA")},
      consequences = {@KeepTarget(memberFromBinding = "AnyMemberOnA")})
  static class A {

    public void foo() {
      System.out.println(new Exception().getStackTrace()[0].getMethodName());
    }

    // TODO(b/265892343): There is no backref support for "any params", thus this method is hit by
    //  the extracted rule.
    public void foo(int unused) {
      System.out.println(new Exception().getStackTrace()[0].getMethodName());
    }

    public void bar() {
      System.out.println(new Exception().getStackTrace()[0].getMethodName());
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      new A().foo();
    }
  }
}
