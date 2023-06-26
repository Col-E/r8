// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepMethodsEmptyAccessFlagsTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("hello", "world");
  static final String EXPECTED_ACCESS_MODIFICATION = StringUtils.lines("hello", "old", "world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public KeepMethodsEmptyAccessFlagsTest(TestParameters parameters) {
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
        // TODO(b/131130038): Should not publicize kept method old().
        .assertSuccessWithOutput(
            parameters.isAccessModificationEnabledByDefault() && parameters.isDexRuntime()
                ? EXPECTED_ACCESS_MODIFICATION
                : EXPECTED)
        .inspect(this::checkOutput);
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, A.class, Abs.class);
  }

  private void checkOutput(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Abs.class);
    assertThat(clazz, isPresent());
    assertThat(clazz.uniqueMethodWithOriginalName("hello"), isPresent());
    assertThat(clazz.uniqueMethodWithOriginalName("my"), isPresent());
    assertThat(clazz.uniqueMethodWithOriginalName("old"), isPresent());
    assertThat(clazz.uniqueMethodWithOriginalName("world"), isPresent());
  }

  abstract static class Abs {
    public abstract void hello();

    public void my() {}

    abstract void old();

    public abstract void world();
  }

  static class A {

    @UsesReflection({
      @KeepTarget(
          kind = KeepItemKind.CLASS_AND_MEMBERS,
          classConstant = Abs.class,
          methodAccess = {
            /* the explicit empty set matches all access */
          })
    })
    void foo() {
      List<String> sorted = new ArrayList<>();
      for (Method method : Abs.class.getDeclaredMethods()) {
        int modifiers = method.getModifiers();
        if (Modifier.isPublic(modifiers) && Modifier.isAbstract(modifiers)) {
          sorted.add(method.getName());
        }
      }
      sorted.sort(String::compareTo);
      for (String string : sorted) {
        System.out.println(string);
      }
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      new A().foo();
    }
  }
}
