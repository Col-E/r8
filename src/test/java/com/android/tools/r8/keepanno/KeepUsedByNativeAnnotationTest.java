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
import com.android.tools.r8.keepanno.annotations.KeepCondition;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByNative;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepUsedByNativeAnnotationTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public KeepUsedByNativeAnnotationTest(TestParameters parameters) {
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
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkOutput);
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, A.class, B.class, C.class);
  }

  private void checkOutput(CodeInspector inspector) {
    assertThat(inspector.clazz(A.class), isPresent());
    assertThat(inspector.clazz(B.class), isPresent());
    assertThat(inspector.clazz(C.class), isAbsent());
    assertThat(inspector.clazz(A.class).method("void", "bar"), isPresent());
    assertThat(inspector.clazz(B.class).method("void", "bar"), isPresent());
    assertThat(inspector.clazz(B.class).method("void", "bar", "int"), isAbsent());
  }

  @UsedByNative(
      description = "Ensure that the class A remains as we are assuming the contents of its name.",
      preconditions = {@KeepCondition(classConstant = A.class, methodName = "foo")},
      // The kind will default to ONLY_CLASS, so setting this to include members will keep the
      // otherwise unused bar method.
      kind = KeepItemKind.CLASS_AND_MEMBERS)
  static class A {

    public void foo() throws Exception {
      Class<?> clazz = Class.forName(A.class.getTypeName().replace("$A", "$B"));
      clazz.getDeclaredMethod("bar").invoke(clazz);
    }

    public void bar() {
      // Unused but kept by the annotation.
    }
  }

  static class B {

    @UsedByNative(
        // Only if A.foo is live do we need to keep this.
        preconditions = {@KeepCondition(classConstant = A.class, methodName = "foo")},
        // Both the class and method are reflectively accessed.
        kind = KeepItemKind.CLASS_AND_MEMBERS)
    public static void bar() {
      System.out.println("Hello, world");
    }

    public static void bar(int ignore) {
      throw new RuntimeException("UNUSED");
    }
  }

  static class C {
    // Unused.
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      new A().foo();
    }
  }
}
