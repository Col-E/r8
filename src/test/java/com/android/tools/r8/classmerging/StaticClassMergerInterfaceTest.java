// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticClassMergerInterfaceTest extends TestBase {

  private final Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public StaticClassMergerInterfaceTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("In A.a()", "In B.b()", "In C.c()");

    CodeInspector inspector =
        testForR8(backend)
            .addInnerClasses(StaticClassMergerInterfaceTest.class)
            .addKeepMainRule(TestClass.class)
            .addKeepRules("-dontobfuscate")
            .enableInliningAnnotations()
            .enableClassInliningAnnotations()
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    // Check that A has not been merged into B. The static class merger visits classes in alpha-
    // betical order. By the time A is processed, there is no merge representative and A is not
    // a valid merge representative itself, because it is an interface.
    assertThat(inspector.clazz(A.class), isPresent());

    // By the time B is processed, there is no merge representative, so it should be present.
    assertThat(inspector.clazz(B.class), isPresent());

    // By the time C is processed, B should be merge candidate. Therefore, we should allow C.c() to
    // be moved to B *although C is an interface*.
    assertThat(inspector.clazz(C.class), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      A.a();
      B.b();
      C.c();
    }
  }

  @NeverClassInline
  interface A {

    @NeverInline
    static void a() {
      System.out.println("In A.a()");
    }
  }

  @NeverClassInline
  static class B {

    @NeverInline
    static void b() {
      System.out.println("In B.b()");
    }
  }

  @NeverClassInline
  interface C {

    @NeverInline
    static void c() {
      System.out.println("In C.c()");
    }
  }
}
