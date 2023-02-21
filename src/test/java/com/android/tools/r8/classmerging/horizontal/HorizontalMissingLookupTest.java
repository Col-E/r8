// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class HorizontalMissingLookupTest extends HorizontalClassMergingTestBase {

  public HorizontalMissingLookupTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(transformer(B.class).removeMethodsWithName("foo").transform())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A::baz", "B::bar");
  }

  public static class A {

    @NeverInline
    public static void baz() {
      System.out.println("A::baz");
    }
  }

  public static class B {
    // Will be removed.
    public static void foo() {
      System.out.println("B::foo");
    }

    @NeverInline
    public static void bar() {
      System.out.println("B::bar");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A.baz();
      if (args.length > 0) {
        B.foo();
      }
      B.bar();
    }
  }
}
