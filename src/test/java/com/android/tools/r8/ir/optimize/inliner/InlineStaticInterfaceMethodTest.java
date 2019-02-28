// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.ir.desugar.InterfaceMethodRewriter.COMPANION_CLASS_NAME_SUFFIX;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;

public class InlineStaticInterfaceMethodTest extends TestBase {

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Hello world!");

    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(InlineStaticInterfaceMethodTest.class)
            .addKeepMainRule(TestClass.class)
            .setMinApi(AndroidApiLevel.M)
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    // TODO(b/124017330): greet() should have been inlined into main().
    ClassSubject classSubject =
        inspector.clazz(I.class.getTypeName() + COMPANION_CLASS_NAME_SUFFIX);
    assertThat(classSubject, isPresent());
    assertThat(classSubject.uniqueMethodWithName("greet"), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      I.greet();
    }
  }

  interface I {

    static void greet() {
      System.out.println("Hello world!");
    }
  }
}
