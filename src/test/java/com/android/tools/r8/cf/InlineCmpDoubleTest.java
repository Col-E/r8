// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KeepUnusedReturnValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InlineCmpDoubleTest extends TestBase {

  private final boolean enableInlining;
  private final TestParameters parameters;

  public InlineCmpDoubleTest(boolean enableInlining, TestParameters parameters) {
    this.enableInlining = enableInlining;
    this.parameters = parameters;
  }

  @Parameters(name = "{1}, inlining: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(options -> options.inlinerOptions().enableInlining = enableInlining)
        .enableKeepUnusedReturnValueAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(TestClass.class);
    MethodSubject method =
        clazz.method(new MethodSignature("inlineMe", "int", ImmutableList.of("int")));
    assertEquals(enableInlining, !method.isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      inlinee(42);
    }

    public static void inlinee(int x) {
      inlineMe(x + 41);
    }

    @KeepUnusedReturnValue
    public static int inlineMe(int x) {
      // Side effect to ensure that the invocation is not removed simply because the method does not
      // have any side effects.
      System.out.println("In InlineCmpDoubleTest.inlineMe()");
      double a = x / 255.0;
      return a < 64.0 ? 42 : 43;
    }
  }
}
