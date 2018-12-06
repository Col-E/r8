// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import org.junit.Ignore;
import org.junit.Test;

/** Regression test for b/120182628. */
public class BuilderWithSelfReferenceTest extends TestBase {

  @Test
  @Ignore("b/120182628")
  public void test() throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(BuilderWithSelfReferenceTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .compile();
  }

  static class TestClass {

    public static void main(String[] args) {
      new Builder().build();
    }
  }

  static class Builder {

    public Builder f = this;

    public Object build() {
      invoke(f);
      return new Object();
    }

    @NeverInline
    public static void invoke(Object o) {}
  }
}
