// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepNameAndInstanceOfTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("on Base", "on Sub");
  static final String EXPECTED_R8 = StringUtils.lines("on Base", "No method on Sub");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public KeepNameAndInstanceOfTest(TestParameters parameters) {
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
        .assertSuccessWithOutput(EXPECTED_R8);
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, Base.class, Sub.class, A.class);
  }

  static class Base {

    static void hiddenMethod() {
      System.out.println("on Base");
    }
  }

  static class Sub extends Base {

    static void hiddenMethod() {
      System.out.println("on Sub");
    }
  }

  static class A {

    @UsesReflection({
      @KeepTarget(
          // Restricting the matching to Base will cause Sub to be stripped.
          classConstant = Base.class,
          instanceOfClassConstant = Base.class,
          methodName = "hiddenMethod")
    })
    public void foo(Base base) throws Exception {
      base.getClass().getDeclaredMethod("hiddenMethod").invoke(null);
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      try {
        new A().foo(new Base());
      } catch (Exception e) {
        System.out.println("No method on Base");
      }
      try {
        new A().foo(new Sub());
      } catch (Exception e) {
        System.out.println("No method on Sub");
      }
    }
  }
}
