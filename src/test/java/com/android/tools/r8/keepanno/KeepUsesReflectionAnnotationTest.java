// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepUsesReflectionAnnotationTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public KeepUsesReflectionAnnotationTest(TestParameters parameters) {
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
    List<String> rules = getExtractedKeepRules();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getInputClassesWithoutAnnotations())
        .addKeepRules(rules)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkOutput);
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, A.class, B.class);
  }

  public List<byte[]> getInputClassesWithoutAnnotations() throws Exception {
    List<Class<?>> classes = getInputClasses();
    List<byte[]> transformed = new ArrayList<>(classes.size());
    for (Class<?> clazz : classes) {
      transformed.add(transformer(clazz).removeAllAnnotations().transform());
    }
    return transformed;
  }

  public List<String> getExtractedKeepRules() throws Exception {
    List<Class<?>> classes = getInputClasses();
    List<String> rules = new ArrayList<>();
    for (Class<?> clazz : classes) {
      rules.addAll(KeepEdgeAnnotationsTest.getKeepRulesForClass(clazz));
    }
    return rules;
  }

  private void checkOutput(CodeInspector inspector) {
    assertThat(inspector.clazz(A.class), isPresent());
    assertThat(inspector.clazz(B.class), isPresent());
    assertThat(inspector.clazz(B.class).uniqueMethodWithOriginalName("bar"), isPresent());
  }

  static class A {

    @UsesReflection({
      // Ensure that the class A remains as we are assuming the contents of its name.
      @KeepTarget(classConstant = A.class),
      // Ensure that the class B remains as we are looking it up by reflected name.
      @KeepTarget(classConstant = B.class),
      // Ensure the method 'bar' remains as we are invoking it by reflected name.
      @KeepTarget(classConstant = B.class, methodName = "bar")
    })
    public void foo() throws Exception {
      Class<?> clazz = Class.forName(A.class.getTypeName().replace("$A", "$B"));
      clazz.getDeclaredMethod("bar").invoke(clazz);
    }
  }

  static class B {
    public static void bar() {
      System.out.println("Hello, world");
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      new A().foo();
    }
  }
}
