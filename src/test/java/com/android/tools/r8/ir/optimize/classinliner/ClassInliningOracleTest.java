// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Ignore;
import org.junit.Test;

/** Regression test for b/121119666. */
public class ClassInliningOracleTest extends TestBase {

  @Ignore("b/121119666")
  @Test
  public void test() throws Exception {
    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(ClassInliningOracleTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .enableClassInliningAnnotations()
            .enableMergeAnnotations()
            .compile()
            .inspector();
    assertThat(inspector.clazz(Builder.class), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      // In order to class inline the Builder, we would need to force-inline the help() method.
      // We can't do this alone, though, since force-inlining of help() would lead to an illegal
      // invoke-super instruction in main().
      Builder builder = new Builder();
      new Helper().help(builder);
      System.out.print(builder.build());
    }
  }

  @NeverMerge
  static class HelperBase {

    @NeverInline
    public void hello() {
      System.out.println("Hello");
    }
  }

  @NeverClassInline
  static class Helper extends HelperBase {

    @NeverInline
    public void help(Builder builder) {
      // TODO(b/120959040): To avoid unused argument removal; should be replaced by a testing rule).
      if (builder != null) {
        super.hello();
      }
    }
  }

  static class Builder {

    @NeverInline
    public Object build() {
      return new Object();
    }
  }
}
