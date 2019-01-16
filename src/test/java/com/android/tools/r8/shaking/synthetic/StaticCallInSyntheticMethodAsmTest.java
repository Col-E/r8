// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.synthetic;

import com.android.tools.r8.AsmTestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StaticCallInSyntheticMethodAsmTest extends AsmTestBase {
  private final Backend backend;

  @Parameterized.Parameters(name = "backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public StaticCallInSyntheticMethodAsmTest(Backend backend) {
    this.backend = backend;
  }

  // class Base {
  //   static void foo() { ... }
  //   static synthetic void bar() { Sub.foo(); }
  // }
  // class Sub extends Base {}
  //
  // As per b/120971047, we do not add synthetic methods to the root set.
  // When running the above example with -dontshrink, the static call in the synthetic method is not
  // traced, so no chance to rebind that call to Base#foo. Then, at the end of dex writing, it hits
  // assertion error in the naming lense that checks if call targets are eligible for renaming.
  @Test
  public void test() throws Exception {
    // TODO(b/122819537): need to trace kotlinc-generated synthetic methods.
    if (backend == Backend.DEX) {
      return;
    }
    testForR8(backend)
        .addProgramClassFileData(Base.dump(), Sub.dump())
        .addKeepRules("-dontshrink")
        .compile();
  }

}
