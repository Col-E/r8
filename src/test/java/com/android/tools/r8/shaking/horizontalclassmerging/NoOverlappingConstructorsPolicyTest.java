// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.horizontalclassmerging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.horizontalclassmerging.EmptyClassTest.A;
import com.android.tools.r8.shaking.horizontalclassmerging.EmptyClassTest.B;
import com.android.tools.r8.shaking.horizontalclassmerging.EmptyClassTest.Main;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NoOverlappingConstructorsPolicyTest extends TestBase {
  private final TestParameters parameters;
  private final boolean enableHorizontalClassMerging;

  public NoOverlappingConstructorsPolicyTest(
      TestParameters parameters, boolean enableHorizontalClassMerging) {
    this.parameters = parameters;
    this.enableHorizontalClassMerging = enableHorizontalClassMerging;
  }

  @Parameterized.Parameters(name = "{0}, horizontalClassMerging:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(this.getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .inspect(
            codeInspector -> {
              if (enableHorizontalClassMerging) {
                assertThat(codeInspector.clazz(A.class), isPresent());
                assertThat(codeInspector.clazz(B.class), isPresent());
                assertThat(codeInspector.clazz(C.class), not(isPresent()));
                // TODO(b/165517236): Explicitly check classes have been merged.
              } else {
                assertThat(codeInspector.clazz(A.class), isPresent());
                assertThat(codeInspector.clazz(B.class), isPresent());
                assertThat(codeInspector.clazz(C.class), isPresent());
              }
            });
  }

  @NeverClassInline
  public static class A {
    public A(String s) {
      System.out.println(s);
    }
  }

  @NeverClassInline
  public static class B {
    public B(String s) {
      System.out.println(s);
    }

    public B(boolean b) {
      System.out.println(b);
    }
  }

  @NeverClassInline
  public static class C {
    public C(boolean b) {
      System.out.println(b);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A("foo");
      System.out.println(a);
      B b1 = new B("");
      System.out.println(b1);
      B b2 = new B(false);
      System.out.println(b2);
      C c = new C(true);
      System.out.println(c);
    }
  }
}
