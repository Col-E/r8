// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import org.junit.Test;

/** Regression test for b/127932803. */
public class FieldMinificationCollisionTest extends TestBase {

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("ABC");
    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(FieldMinificationCollisionTest.class)
            .addKeepMainRule(TestClass.class)
            .addKeepRules(
                "-keep class " + B.class.getTypeName() + " { public java.lang.String f2; }")
            .enableMemberValuePropagationAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableInliningAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    FieldSubject f1Subject = inspector.clazz(A.class).uniqueFieldWithOriginalName("f1");
    assertThat(f1Subject, isPresent());

    FieldSubject f3Subject = inspector.clazz(C.class).uniqueFieldWithOriginalName("f3");
    assertThat(f3Subject, isPresent());

    assertNotEquals(f1Subject.getFinalName(), f3Subject.getFinalName());
  }

  static class TestClass {

    public static void main(String[] args) {
      new C("A", "B", "C").print();
    }
  }

  @NoVerticalClassMerging
  static class A {

    @NeverPropagateValue public String f1;

    public A(String f1) {
      this.f1 = f1;
    }
  }

  @NoVerticalClassMerging
  static class B extends A {

    @NeverPropagateValue public String f2;

    public B(String f1, String f2) {
      super(f1);
      this.f2 = f2;
    }
  }

  @NeverClassInline
  static class C extends B {

    @NeverPropagateValue public String f3;

    public C(String f1, String f2, String f3) {
      super(f1, f2);
      this.f3 = f3;
    }

    @NeverInline
    public void print() {
      System.out.println(f1 + f2 + f3);
    }
  }
}
