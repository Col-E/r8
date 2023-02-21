// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IllegalInliningOfMergedConstructorTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().withAllApiLevels().build();
  }

  public IllegalInliningOfMergedConstructorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(Reprocess.class))
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(B.class, A.class))
        .addOptionsModification(
            options -> options.inlinerOptions().simpleInliningInstructionLimit = 4)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello w0rld!");
  }

  static class Main {

    public static void main(String[] args) {
      new A((System.currentTimeMillis() > 0 ? Reprocess.A : Reprocess.B)).foo();
    }
  }

  @NeverClassInline
  static class A {

    A(Reprocess r) {
      new B().foo();

      // This will force this constructor to be reprocessed, which will cause the inliner to attempt
      // to inline B.<init>() into A.<init>(). Since B.<init>() has been moved to A as a result of
      // horizontal class merging, the inliner will check if B.<init>() is eligible for inlining,
      // which it is not in this case, due the assignment of $r8$classId.
      System.out.print(r.ordinal());
    }

    @NeverInline
    void foo() {
      System.out.println("rld!");
    }
  }

  @NeverClassInline
  static class B {

    public B() {}

    @NeverInline
    void foo() {
      System.out.print("Hello w");
    }
  }

  enum Reprocess {
    A,
    B
  }
}
