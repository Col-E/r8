// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compatproguard.ifrules;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.shaking.methods.MethodsTestBase.Shrinker;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConditionalOnInlinedFieldTest extends TestBase {

  static class A {

    int field = 42;

    void method(String name, int field) throws Exception {
      if (field == 42) {
        Class<?> clazz = Class.forName(name);
        Object object = clazz.getDeclaredConstructor().newInstance();
        clazz.getDeclaredMethod("method").invoke(object);
      }
    }
  }

  static class B {
    void method() {
      System.out.println("B::method");
    }
  }

  static class MainWithFieldReference {

    public static void main(String[] args) throws Exception {
      A a = new A();
      a.method(args[0], a.field);
    }
  }

  static class MainWithoutFieldReference {

    public static void main(String[] args) throws Exception {
      A a = new A();
      a.method(args[0], 42);
    }
  }

  private static String EXPECTED = StringUtils.lines("B::method");

  @Parameters(name = "{0}, {1}, ref:{2}")
  public static List<Object[]> data() {
    return buildParameters(
        Shrinker.values(), getTestParameters().withCfRuntimes().build(), BooleanUtils.values());
  }

  private final Shrinker shrinker;
  private final TestParameters parameters;
  private final boolean withFieldReference;

  public ConditionalOnInlinedFieldTest(
      Shrinker shrinker, TestParameters parameters, boolean withFieldReference) {
    this.shrinker = shrinker;
    this.parameters = parameters;
    this.withFieldReference = withFieldReference;
  }

  private Class<?> getMain() {
    return withFieldReference ? MainWithFieldReference.class : MainWithoutFieldReference.class;
  }

  @Test
  public void testReference() throws Exception {
    testForJvm()
        .addProgramClasses(getMain(), A.class, B.class)
        .run(parameters.getRuntime(), getMain(), B.class.getTypeName())
        .assertSuccessWithOutput(EXPECTED);
  }

  private TestShrinkerBuilder<?, ?, ?, ?, ?> buildShrinker() throws Exception {
    TestShrinkerBuilder<?, ?, ?, ?, ?> builder;
    if (shrinker == Shrinker.Proguard) {
      builder =
          testForProguard()
              .addKeepRules("-dontwarn " + ConditionalOnInlinedFieldTest.class.getTypeName());
    } else if (shrinker == Shrinker.R8Compat) {
      builder = testForR8Compat(parameters.getBackend());
    } else {
      builder = testForR8(parameters.getBackend());
    }
    return builder
        .addProgramClasses(getMain(), A.class, B.class)
        .addKeepMainRule(getMain())
        .addKeepRules(
            "-if class "
                + A.class.getTypeName()
                + " { int field; }"
                + " -keep class "
                + B.class.getTypeName()
                + " { void <init>(); void method(); }");
  }

  @Test
  public void testConditionalOnField() throws Exception {
    TestRunResult<?> result =
        buildShrinker().compile().run(parameters.getRuntime(), getMain(), B.class.getTypeName());
    if (!withFieldReference && shrinker != Shrinker.Proguard) {
      // Without the reference we expect an error. For some reason PG keeps in any case.
      result.assertFailureWithErrorThatThrows(ClassNotFoundException.class);
    } else {
      result.assertSuccessWithOutput(EXPECTED);
    }
  }
}
