// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.cost;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NonMaterializingFieldAccessesAfterClassInliningTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public NonMaterializingFieldAccessesAfterClassInliningTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(NonMaterializingFieldAccessesAfterClassInliningTest.class)
        .addKeepMainRule(TestClass.class)
        // Should be able to class inline Builder even when the threshold is low.
        .addOptionsModification(
            options ->
                options.classInlinerOptions().classInliningInstructionAllowance =
                    parameters.isCfRuntime() ? 3 : 6)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(Greeter.class), isPresent());
    assertThat(inspector.clazz(Builder.class), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(
          new Builder()
              .setC1('H')
              .setC2('e')
              .setC3('l')
              .setC4('l')
              .setC5('o')
              .setC6(' ')
              .setC7('w')
              .setC8('o')
              .setC9('r')
              .setC10('l')
              .setC11('d')
              .setC12('!')
              .build());
    }
  }

  static class Greeter {

    final char c1;
    final char c2;
    final char c3;
    final char c4;
    final char c5;
    final char c6;
    final char c7;
    final char c8;
    final char c9;
    final char c10;
    final char c11;
    final char c12;

    @NeverInline
    Greeter(
        char c1,
        char c2,
        char c3,
        char c4,
        char c5,
        char c6,
        char c7,
        char c8,
        char c9,
        char c10,
        char c11,
        char c12) {
      this.c1 = c1;
      this.c2 = c2;
      this.c3 = c3;
      this.c4 = c4;
      this.c5 = c5;
      this.c6 = c6;
      this.c7 = c7;
      this.c8 = c8;
      this.c9 = c9;
      this.c10 = c10;
      this.c11 = c11;
      this.c12 = c12;
    }

    @Override
    public String toString() {
      return Character.toString(c1) + c2 + c3 + c4 + c5 + c6 + c7 + c8 + c9 + c10 + c11 + c12;
    }
  }

  @NoHorizontalClassMerging
  static class Builder {

    char c1;
    char c2;
    char c3;
    char c4;
    char c5;
    char c6;
    char c7;
    char c8;
    char c9;
    char c10;
    char c11;
    char c12;

    @NeverInline
    Builder setC1(char c) {
      this.c1 = c;
      return this;
    }

    @NeverInline
    Builder setC2(char c) {
      this.c2 = c;
      return this;
    }

    @NeverInline
    Builder setC3(char c) {
      this.c3 = c;
      return this;
    }

    @NeverInline
    Builder setC4(char c) {
      this.c4 = c;
      return this;
    }

    @NeverInline
    Builder setC5(char c) {
      this.c5 = c;
      return this;
    }

    @NeverInline
    Builder setC6(char c) {
      this.c6 = c;
      return this;
    }

    @NeverInline
    Builder setC7(char c) {
      this.c7 = c;
      return this;
    }

    @NeverInline
    Builder setC8(char c) {
      this.c8 = c;
      return this;
    }

    @NeverInline
    Builder setC9(char c) {
      this.c9 = c;
      return this;
    }

    @NeverInline
    Builder setC10(char c) {
      this.c10 = c;
      return this;
    }

    @NeverInline
    Builder setC11(char c) {
      this.c11 = c;
      return this;
    }

    @NeverInline
    Builder setC12(char c) {
      this.c12 = c;
      return this;
    }

    @NeverInline
    Greeter build() {
      return new Greeter(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12);
    }
  }
}
