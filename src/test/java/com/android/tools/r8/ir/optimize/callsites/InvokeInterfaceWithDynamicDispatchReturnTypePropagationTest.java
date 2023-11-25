// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.callsites;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeInterfaceWithDynamicDispatchReturnTypePropagationTest extends TestBase {

  @Parameter(0)
  public boolean nonNullFromSiblingMethod;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, nonNullFromSiblingMethod: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-assumenosideeffects class " + A.class.getTypeName() + " {",
            "  static boolean NON_NULL return " + nonNullFromSiblingMethod + ";",
            "}")
        .enableInliningAnnotations()
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());
              assertThat(
                  mainClassSubject.uniqueMethodWithOriginalName("dead"),
                  isAbsentIf(nonNullFromSiblingMethod));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  static class Main {

    public static void main(String[] args) {
      I i = System.currentTimeMillis() > 0 ? new B() : new C();
      Object o = i.m();
      if (o == null) {
        dead();
      }
    }

    @NeverInline
    static void dead() {
      System.out.println("Dead!");
    }
  }

  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface I {

    Object m();
  }

  @NoVerticalClassMerging
  static class A {

    static boolean NON_NULL;

    public Object m() {
      if (NON_NULL) {
        return new Object();
      } else {
        return System.currentTimeMillis() > 0 ? new Object() : null;
      }
    }
  }

  @NoVerticalClassMerging
  static class B extends A implements I {}

  static class C extends B {

    @Override
    public Object m() {
      return new Object();
    }
  }
}
