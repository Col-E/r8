// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import org.junit.Test;

public class ClassesWithNativeMethodsTest extends HorizontalClassMergingTestBase {
  public ClassesWithNativeMethodsTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .addKeepPackageNamesRule(Main.class.getPackage())
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .run(parameters.getRuntime(), Main.class)
        .inspectFailure(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isPresent());
            })
        .assertFailureWithErrorThatMatches(
            allOf(
                containsString("java.lang.UnsatisfiedLinkError:"),
                containsString("com.android.tools.r8.classmerging.horizontal.b.a(")));
  }

  @NeverClassInline
  public static class A {
    @NeverInline
    public A() {
      System.out.println("a");
    }

    @NeverInline
    public String foo() {
      return "foo";
    }
  }

  @NeverClassInline
  public static class B {
    public B() {
      System.out.println("b");
    }

    public native String foo();
  }

  public static class Main {
    public static void main(String[] args) {
      System.out.println(new A().foo());
      System.out.println(new B().foo());
    }
  }
}
