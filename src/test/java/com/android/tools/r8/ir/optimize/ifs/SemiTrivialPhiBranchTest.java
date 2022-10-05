// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.ifs;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SemiTrivialPhiBranchTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public SemiTrivialPhiBranchTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(SemiTrivialPhiBranchTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Live!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());
    assertThat(testClassSubject.mainMethod(), isPresent());
    assertThat(testClassSubject.uniqueMethodWithOriginalName("live"), isPresent());
    assertThat(testClassSubject.uniqueMethodWithOriginalName("dead"), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      boolean x;
      if (System.currentTimeMillis() >= 0) {
        x = true;
      } else {
        x = new BooleanBox(true).get();
      }
      // After class inlining, the two operands to the phi become true. However, the phi does not
      // become trivial until after canonicalization. Therefore, it is important that we run branch
      // pruning after canonicalization.
      if (x) {
        live();
      } else {
        dead();
      }
    }

    @NeverInline
    static void live() {
      System.out.println("Live!");
    }

    @NeverInline
    static void dead() {
      System.out.println("Dead!");
    }
  }

  static class BooleanBox {

    boolean value;

    BooleanBox(boolean value) {
      this.value = value;
    }

    boolean get() {
      return value;
    }
  }
}
