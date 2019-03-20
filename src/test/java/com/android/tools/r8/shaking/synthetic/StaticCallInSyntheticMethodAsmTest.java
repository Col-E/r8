// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.synthetic;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.ToolHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StaticCallInSyntheticMethodAsmTest extends AsmTestBase {
  private final Backend backend;

  @Parameterized.Parameters(name = "backend: {0}")
  public static Backend[] data() {
    return ToolHelper.getBackends();
  }

  public StaticCallInSyntheticMethodAsmTest(Backend backend) {
    this.backend = backend;
  }

  // class Base {
  //   static void foo() { ... }
  //   static synthetic void bar() { Sub.foo(); }
  // }
  // class Sub extends Base {}
  @Test
  public void test() throws Exception {
    testForR8(backend)
        .addProgramClassFileData(Base.dump(), Sub.dump())
        .addKeepRules("-dontshrink")
        .compile();
  }

}
