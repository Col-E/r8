// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class MergedVirtualMethodStaticizerTest extends HorizontalClassMergingTestBase {
  public MergedVirtualMethodStaticizerTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(Program.class)
        .addKeepClassAndMembersRules(Program.Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(Program.B.class, Program.A.class))
        .run(parameters.getRuntime(), Program.Main.class)
        .assertSuccessWithOutputLines("A::foo", "Staticized::foo", "B::foo");
  }

  public static class Program {

    @NeverClassInline
    public static class Staticized {
      public static final Staticized staticized = new Staticized();

      @NeverInline
      public void foo() {
        System.out.println("Staticized::foo");
      }
    }

    @NeverClassInline
    public static class A {

      public void foo() {
        System.out.println("A::foo");
        Staticized.staticized.foo();
      }
    }

    @NeverClassInline
    public static class B {

      public void foo() {
        System.out.println("B::foo");
      }
    }

    public static class Main {

      public static void main(String[] args) {
        new A().foo();
        new B().foo();
      }
    }
  }
}
