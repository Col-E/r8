// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.interfacemethods;

import static com.android.tools.r8.ir.desugar.InterfaceMethodRewriter.COMPANION_CLASS_NAME_SUFFIX;
import static com.android.tools.r8.ir.desugar.InterfaceMethodRewriter.DEFAULT_METHOD_PREFIX;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;

public class InlineDefaultInterfaceMethodTest extends TestBase {

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Hello world!");

    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(InlineDefaultInterfaceMethodTest.class)
            .addKeepMainRule(TestClass.class)
            .setMinApi(AndroidApiLevel.M)
            .enableClassInliningAnnotations()
            .enableMergeAnnotations()
            .noMinification()
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    // TODO(b/124017330): interface methods should have been inlined into C.method().
    ClassSubject classSubject =
        inspector.clazz(I.class.getTypeName() + COMPANION_CLASS_NAME_SUFFIX);
    assertThat(classSubject, isPresent());
    assertThat(classSubject.uniqueMethodWithName(DEFAULT_METHOD_PREFIX + "hello"), isPresent());
    assertThat(classSubject.uniqueMethodWithName(DEFAULT_METHOD_PREFIX + "space"), isPresent());
    assertThat(classSubject.uniqueMethodWithName(DEFAULT_METHOD_PREFIX + "world"), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      C obj = new C();
      obj.method();
    }
  }

  @NeverMerge
  interface I {

    default void hello() {
      System.out.print("Hello");
    }

    default void space() {
      System.out.print(" ");
    }

    default void world() {
      System.out.println("world!");
    }
  }

  @NeverClassInline
  static class C implements I {

    @NeverInline
    public void method() {
      // invoke-virtual
      hello();
      // invoke-interface
      I self = this;
      self.space();
      // invoke-super
      I.super.world();
    }
  }
}
