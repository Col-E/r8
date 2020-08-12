// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.horizontalclassmerging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.*;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IdenticalFieldMembersTest extends TestBase {
  private final TestParameters parameters;
  private final boolean enableHorizontalClassMerging;

  public IdenticalFieldMembersTest(
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
        .addInnerClasses(IdenticalFieldMembersTest.class)
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo A", "bar B")
        .inspect(
            codeInspector -> {
              if (enableHorizontalClassMerging) {
                // TODO(b/163311975): A and B should be merged
                //
                //                        Class[] classes = {A.class, B.class};
                //                        assertEquals(1, Arrays.stream(classes)
                //                                .filter(a -> codeInspector.clazz(a).isPresent())
                //                                .count());
                assertThat(codeInspector.clazz(A.class), isPresent());
                assertThat(codeInspector.clazz(B.class), isPresent());
              } else {
                assertThat(codeInspector.clazz(A.class), isPresent());
                assertThat(codeInspector.clazz(B.class), isPresent());
              }
            });
  }

  @NeverClassInline
  public static class A {
    private String field;

    public A(String v) {
      this.field = v;
    }

    @NeverInline
    public void foo() {
      System.out.println("foo " + field);
    }
  }

  @NeverClassInline
  public static class B {
    private String field;

    public B(String v) {
      this.field = v;
    }

    @NeverInline
    public void bar() {
      System.out.println("bar " + field);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A("A");
      a.foo();
      B b = new B("B");
      b.bar();
    }
  }
}
