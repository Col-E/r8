// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassHierarchyVerifier;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Ignore;
import org.junit.Test;

public class B115705526 extends TestBase {

  @Ignore("b/115705526")
  @Test
  public void test() throws Exception {
    AndroidApp input =
        readClasses(
            B115705526TestClass.class,
            B115705526TestClass.A.class,
            B115705526TestClass.B.class,
            B115705526TestClass.C.class,
            B115705526TestClass.D.class);
    AndroidApp output =
        compileWithR8(
            input,
            keepMainProguardConfiguration(B115705526TestClass.class),
            options -> {
              options.enableClassInlining = false;
              options.enableInlining = false;
              options.enableMinification = false;
            });
    CodeInspector inspector = new CodeInspector(output);
    new ClassHierarchyVerifier(inspector).run();
  }
}

class B115705526TestClass {

  public static void main(String[] args) {
    C obj = new C();
    obj.bar();
    new D();
  }

  public abstract static class A {

    // Will be marked as targeted because the call in B.bar() resolves to "int A.foo()".
    // Since it is targeted, class A will also have the method foo in the generated output.
    public abstract int foo();
  }

  public abstract static class B extends A {

    public int bar() {
      return foo();
    }
  }

  public static class C extends B {

    public int foo() {
      return 42;
    }
  }

  // Since D is non-abstract, and since method foo() is kept in A, class D should continue to
  // implement method foo().
  public static class D extends A {

    @Override
    public int foo() {
      return 42;
    }
  }
}
