// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.classmerging.horizontal.testclasses.C;
import com.android.tools.r8.classmerging.horizontal.testclasses.D;
import org.junit.Test;

public class PackagePrivateMembersAccessedTest extends HorizontalClassMergingTestBase {
  public PackagePrivateMembersAccessedTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addProgramClasses(C.class)
        .addProgramClasses(D.class)
        .addKeepMainRule(Main.class)
        .allowAccessModification(false)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo", "hello", "5", "foobar")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(C.class), isPresent());
              assertThat(codeInspector.clazz(D.class), isPresent());
              assertThat(codeInspector.clazz(E.class), isPresent());
            });
  }

  @NeverClassInline
  public static class E {
    @NeverInline
    public E(int v) {
      System.out.println(v);
    }

    public void foobar() {
      System.out.println("foobar");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      D d = new D("foo");
      C c = d.getC("hello");
      c.bar();
      E e = new E(5);
      e.foobar();
    }
  }
}
