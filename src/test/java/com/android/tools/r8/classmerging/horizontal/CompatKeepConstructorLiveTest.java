// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;

public class CompatKeepConstructorLiveTest extends HorizontalClassMergingTestBase {
  public CompatKeepConstructorLiveTest(
      TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void testR8() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("b: main", "true")
        .inspect(
            codeInspector -> {
              ClassSubject aClassSubject = codeInspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              assertThat(aClassSubject.init(), isPresent());

              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(
                  codeInspector.clazz(B.class), notIf(isPresent(), enableHorizontalClassMerging));
            });
  }

  @NeverClassInline
  public static class A {}

  @NeverClassInline
  public static class B {
    public B(String v) {
      System.out.println("b: " + v);
    }
  }

  public static class Main {
    public static void main(String[] args) throws Exception {
      new B("main");
      System.out.println(A.class.toString().length() > 0);
    }
  }
}
