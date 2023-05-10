// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;

public class CompatKeepConstructorLiveTest extends HorizontalClassMergingTestBase {
  public CompatKeepConstructorLiveTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("b: main", "true")
        .inspect(
            codeInspector -> {
              ClassSubject aClassSubject = codeInspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertThat(aClassSubject.init(), isPresent());

              ClassSubject bClassSubject = codeInspector.clazz(B.class);
              assertThat(bClassSubject, isPresent());
              assertThat(bClassSubject.init(), isPresent());
            });
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class A {}

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class B {
    @NeverInline
    public B(String v) {
      System.out.println("b: " + v);
    }
  }

  public static class Main {
    public static void main(String[] args) {
      new B("main");
      System.out.println(A.class.toString().length() > 0);
    }
  }
}
