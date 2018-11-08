// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.devirtualize;

import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import inlining.NeverInline;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DevirtualizerNonNullRewritingTest extends TestBase {

  private final Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public DevirtualizerNonNullRewritingTest(Backend backend) {
    this.backend = backend;
  }

  /**
   * Regression test for b/119168882.
   */
  @Test
  public void test() throws Exception {
    String expectedOutput = "Hello!";

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expectedOutput);

    testForR8(backend)
        .addInnerClasses(DevirtualizerNonNullRewritingTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableMergeAnnotations()
        .run(TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  static class TestClass {

    private static Interface field;

    public static void main(String[] args) {
      test(new B());

      // Print the field to prevent that the field is removed by dead code elimination.
      System.out.print(field);
    }

    @NeverInline
    public static void test(Interface obj) {
      // Invoke-interface instruction will be rewritten to invoke-virtual by the devirtualizer.
      // In order to call A.method() directly, a check-cast instruction will be inserted that casts
      // `obj` from Interface to A.
      obj.method();

      // In the IR we will insert a NonNull instruction for `obj` here. It is unsound to replace
      // the in-value of the NonNull instruction by the out-value of the cast, since A is not a
      // subtype of Interface. Doing so would lead to a type error in the following static-put
      // instruction, because `obj` is used as an Interface.
      field = obj;
    }
  }

  @NeverMerge
  interface Interface {

    @NeverInline
    void method();
  }

  @NeverMerge
  static class A {

    @NeverInline
    public void method() {}

    @Override
    public String toString() {
      return "Hello!";
    }
  }

  static class B extends A implements Interface {}
}
