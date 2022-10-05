// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests that the fields of two interfaces I and J are given distinct names, when the interfaces
 * have a common sub class, which does not implement both interfaces directly.
 */
@RunWith(Parameterized.class)
public class ReservedFieldNameInInterfacesWithCommonSubClassTest extends TestBase {

  private final boolean reserveName;

  @Parameterized.Parameters(name = "Reserve name: {0}")
  public static Boolean[] data() {
    return BooleanUtils.values();
  }

  public ReservedFieldNameInInterfacesWithCommonSubClassTest(boolean reserveName) {
    this.reserveName = reserveName;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Hello world!");
    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addProgramClasses(TestClass.class, A.class, B.class, I.class, J.class)
            .enableNoVerticalClassMergingAnnotations()
            .addKeepMainRule(TestClass.class)
            .addKeepRules(
                reserveName
                    ? "-keepclassmembernames class "
                        + J.class.getTypeName()
                        + "{ java.lang.String a; }"
                    : "")
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject iClassSubject = inspector.clazz(I.class);
    assertThat(iClassSubject, isPresent());

    ClassSubject jClassSubject = inspector.clazz(J.class);
    assertThat(jClassSubject, isPresent());

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());

    FieldSubject f1FieldSubject = iClassSubject.uniqueFieldWithOriginalName("f1");
    assertThat(f1FieldSubject, isPresent());

    FieldSubject aFieldSubject = jClassSubject.uniqueFieldWithOriginalName("a");
    assertThat(aFieldSubject, isPresent());

    if (reserveName) {
      assertEquals("b", f1FieldSubject.getFinalName());
      assertEquals("a", aFieldSubject.getFinalName());
    } else {
      assertThat(f1FieldSubject.getFinalName(), anyOf(is("a"), is("b")));
      assertThat(aFieldSubject.getFinalName(), anyOf(is("a"), is("b")));
      assertNotEquals(aFieldSubject.getFinalName(), f1FieldSubject.getFinalName());
    }
  }

  @NoVerticalClassMerging
  interface I {

    String f1 = System.currentTimeMillis() >= 0 ? "Hello " : null;
  }

  @NoVerticalClassMerging
  interface J {

    String a = System.currentTimeMillis() >= 0 ? "world!" : null;
  }

  @NoVerticalClassMerging
  static class A implements I {}

  @NoVerticalClassMerging
  static class B extends A implements J {

    @Override
    public String toString() {
      return f1 + a;
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new B());
    }
  }
}
