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
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.MemberAccessFlags;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepMembersAccessFlagsTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world", "bar");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public KeepMembersAccessFlagsTest(TestParameters parameters) {
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
    return ImmutableList.of(TestClass.class, A.class);
  }

  private void checkOutput(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(A.class);
    assertThat(clazz, isPresent());
    assertThat(clazz.uniqueFieldWithOriginalName("staticField"), isAbsent());
    assertThat(clazz.uniqueFieldWithOriginalName("fieldA"), isPresent());
    assertThat(clazz.uniqueFieldWithOriginalName("fieldB"), isAbsent());
    assertThat(clazz.uniqueMethodWithOriginalName("bar"), isPresent());
    assertThat(clazz.uniqueMethodWithOriginalName("baz"), isAbsent());
    assertThat(clazz.uniqueMethodWithOriginalName("foobar"), isAbsent());
  }

  static class A {

    public static String staticField = "the static";

    public String fieldA = "Hello, world";

    private Integer fieldB = 42;

    @UsesReflection({
      @KeepTarget(
          classConstant = A.class,
          memberAccess = {MemberAccessFlags.PUBLIC, MemberAccessFlags.NON_STATIC})
    })
    void foo() throws Exception {
      String[] fieldNames = new String[] {"fieldA"};
      for (String fieldName : fieldNames) {
        Field field = getClass().getDeclaredField(fieldName);
        int modifiers = field.getModifiers();
        if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
          System.out.println(field.get(this));
        }
      }
      String[] methodNames = new String[] {"bar"};
      for (String methodName : methodNames) {
        Method method = getClass().getDeclaredMethod(methodName);
        int modifiers = method.getModifiers();
        if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
          System.out.println(method.getName());
        }
      }
    }

    public void bar() {}

    private void baz() {}

    public static void foobar() {}
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      new A().foo();
    }
  }
}
