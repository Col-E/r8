// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
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
import java.lang.reflect.Field;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepUsesReflectionOnFieldTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public KeepUsesReflectionOnFieldTest(TestParameters parameters) {
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
        .compile()
        .apply(
            c -> {
              String rules = c.getProguardConfiguration().getParsedConfiguration();
              assertThat(rules, containsString("context: " + descriptor(A.class) + "foo()V"));
              assertThat(rules, containsString("description: Keep the\\nstring-valued fields"));
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkOutput);
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, A.class);
  }

  private void checkOutput(CodeInspector inspector) {
    assertThat(inspector.clazz(A.class), isPresent());
    assertThat(inspector.clazz(A.class).uniqueFieldWithOriginalName("fieldA"), isPresent());
    assertThat(inspector.clazz(A.class).uniqueFieldWithOriginalName("fieldB"), isAbsent());
  }

  static class A {

    public String fieldA = "Hello, world";
    public Integer fieldB = 42;

    @UsesReflection(
        description = "Keep the\nstring-valued fields",
        value = {
          @KeepTarget(
              className = "com.android.tools.r8.keepanno.KeepUsesReflectionOnFieldTest$A",
              fieldType = "java.lang.String")
        })
    public void foo() throws Exception {
      for (Field field : getClass().getDeclaredFields()) {
        if (field.getType().equals(String.class)) {
          System.out.println(field.get(this));
        }
      }
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      new A().foo();
    }
  }
}
