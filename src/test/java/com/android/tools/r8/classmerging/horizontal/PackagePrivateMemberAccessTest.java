// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.classmerging.horizontal.testclasses.A;
import com.android.tools.r8.classmerging.horizontal.testclasses.B;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

public class PackagePrivateMemberAccessTest extends HorizontalClassMergingTestBase {

  public PackagePrivateMemberAccessTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addProgramClasses(A.class, B.class)
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.testing.validInliningReasons = ImmutableSet.of(Reason.FORCE))
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo", "B", "bar", "0", "foobar")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isAbsent());
              assertThat(codeInspector.clazz(B.class), isAbsent());
              assertThat(codeInspector.clazz(C.class), isPresent());
            });
  }

  @NeverClassInline
  public static class C {
    public C(int v) {
      System.out.println(v);
    }

    public void foobar() {
      System.out.println("foobar");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A();
      a.foo();
      B b = a.get("B");
      b.bar();
      C c = new C(args.length);
      c.foobar();
    }
  }
}
