// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import com.android.tools.r8.TestBase;
import org.junit.Test;

public class Regress136250031 extends TestBase {

  @Test
  public void test() throws Exception {
        testForR8(Backend.CF)
            .addInnerClasses(Regress136250031.class)
            .addKeepMainRule(TestClass.class)
            .addKeepClassAndMembersRules(B.class)
            .run(TestClass.class)
            .assertSuccess();
  }

  static class TestClass {
    public static void main(String[] args) {
      new B(new C());
    }
  }

  static class A {
    A(String s) {
      System.out.println(s);
    }
  }

  static class B extends A {
    B(C c) {
      super(c.instance.toString());
    }
  }

  static class C {
    public C instance;

    C() {
      instance = System.currentTimeMillis() > 0 ? this : null;
    }

    @Override
    public String toString() {
      return "42";
    }
  }
}
