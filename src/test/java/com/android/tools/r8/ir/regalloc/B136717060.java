// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.regalloc;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B136717060 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  public B136717060(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void checkSomething(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
  }

  @Test
  public void test() throws Exception {
    testForD8()
        .addInnerClasses(B136717060.class)
        .setMinApi(parameters.getRuntime())
        .compile()
        .disassemble()
        .inspect(this::checkSomething)
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatMatches(
            anyOf(containsString("rejecting opcode"), containsString("Precise Low-half Constant")));
  }

  static class TestClass {

    public static void main(String[] args) {
      test();
    }

    static void test() {
      method(3L, 3L, 1L, 0L);
      method(Long.MAX_VALUE, 1, Long.MAX_VALUE, 0L);
      method(Long.MIN_VALUE, 3L, Long.MIN_VALUE / 3L - 1L, 1L);
      method(Long.MIN_VALUE + 1, -1, Long.MAX_VALUE, 0L);
      method(Long.MIN_VALUE, -1, Long.MIN_VALUE, 0L);
    }

    static void method(long x, long y, Object divExpected, Object modExpected) {}
  }
}
