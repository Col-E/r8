// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverMerge;
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
 * Tests that a reserved field name from a given class is taken into account when renaming the
 * fields in supertypes of that class.
 */
@RunWith(Parameterized.class)
public class ReservedFieldNameInSubClassTest extends TestBase {

  private final boolean reserveName;

  @Parameterized.Parameters(name = "Reserve name: {0}")
  public static Boolean[] data() {
    return BooleanUtils.values();
  }

  public ReservedFieldNameInSubClassTest(boolean reserveName) {
    this.reserveName = reserveName;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Hello world!");
    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addProgramClasses(
                TestClass.class, A.class, B.class, C.class, I.class, J.class, K.class)
            .enableMergeAnnotations()
            .addKeepMainRule(TestClass.class)
            .addKeepRules(
                reserveName
                    ? "-keepclassmembernames class "
                        + C.class.getTypeName()
                        + "{ java.lang.String a; }"
                    : "")
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());

    ClassSubject cClassSubject = inspector.clazz(C.class);
    assertThat(cClassSubject, isPresent());

    ClassSubject iClassSubject = inspector.clazz(I.class);
    assertThat(iClassSubject, isPresent());

    ClassSubject jClassSubject = inspector.clazz(J.class);
    assertThat(jClassSubject, isPresent());

    ClassSubject kClassSubject = inspector.clazz(K.class);
    assertThat(kClassSubject, isPresent());

    FieldSubject f1FieldSubject = aClassSubject.uniqueFieldWithName("f1");
    assertThat(f1FieldSubject, isPresent());

    FieldSubject f2FieldSubject = bClassSubject.uniqueFieldWithName("f2");
    assertThat(f2FieldSubject, isPresent());

    FieldSubject f3FieldSubject = iClassSubject.uniqueFieldWithName("f3");
    assertThat(f3FieldSubject, isPresent());

    FieldSubject f4FieldSubject = jClassSubject.uniqueFieldWithName("f4");
    assertThat(f4FieldSubject, isPresent());

    FieldSubject f5FieldSubject = kClassSubject.uniqueFieldWithName("f5");
    assertThat(f5FieldSubject, isPresent());

    FieldSubject aFieldSubject = cClassSubject.uniqueFieldWithName("a");
    assertThat(aFieldSubject, isPresent());

    if (reserveName) {
      // TODO(b/128973195): A.f1 should be renamed to e instead of a.
      assertThat(f1FieldSubject, isRenamed());
      assertEquals("a", f1FieldSubject.getFinalName());

      // TODO(b/128973195): B.f2 should be renamed to f instead of a.
      assertThat(f2FieldSubject, isRenamed());
      assertEquals("a", f2FieldSubject.getFinalName());

      // TODO(b/128973195): I.f3 should be renamed to b instead of a.
      assertThat(f3FieldSubject, isRenamed());
      assertEquals("a", f3FieldSubject.getFinalName());

      // TODO(b/128973195): J.f4 should be renamed to c instead of b.
      assertThat(f4FieldSubject, isRenamed());
      assertEquals("b", f4FieldSubject.getFinalName());

      // TODO(b/128973195): K.f5 should be renamed to d instead of a.
      assertThat(f5FieldSubject, isRenamed());
      assertEquals("a", f5FieldSubject.getFinalName());

      // B.a should not be renamed because it is not allowed to be minified.
      assertThat(aFieldSubject, not(isRenamed()));
    } else {
      // TODO(b/128973195): A.f1 should be renamed to d instead of a.
      assertThat(f1FieldSubject, isRenamed());
      assertEquals("a", f1FieldSubject.getFinalName());

      // TODO(b/128973195): B.f2 should be renamed to e instead of b.
      assertThat(f2FieldSubject, isRenamed());
      assertEquals("b", f2FieldSubject.getFinalName());

      // TODO(b/128973195): I.f3 should be renamed to a instead of a.
      assertThat(f3FieldSubject, isRenamed());
      assertEquals("a", f3FieldSubject.getFinalName());

      assertThat(f4FieldSubject, isRenamed());
      assertEquals("b", f4FieldSubject.getFinalName());

      // TODO(b/128973195): K.f5 should be renamed to c instead of a.
      assertThat(f5FieldSubject, isRenamed());
      assertEquals("a", f5FieldSubject.getFinalName());

      // TODO(b/128973195): C.a should be renamed to f instead of c.
      assertThat(aFieldSubject, isRenamed());
      assertEquals("c", aFieldSubject.getFinalName());
    }
  }

  @NeverMerge
  static class A {

    String f1 = "He";
  }

  @NeverMerge
  static class B extends A {

    String f2 = "l";
  }

  @NeverMerge
  interface I {

    String f3 = System.currentTimeMillis() >= 0 ? "lo" : null;
  }

  @NeverMerge
  interface J extends I {

    String f4 = System.currentTimeMillis() >= 0 ? " " : null;
  }

  @NeverMerge
  interface K {

    String f5 = System.currentTimeMillis() >= 0 ? "world" : null;
  }

  static class C extends B implements J, K {

    String a = "!";

    @Override
    public String toString() {
      return f1 + f2 + f3 + f4 + f5 + a;
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new C());
    }
  }
}
