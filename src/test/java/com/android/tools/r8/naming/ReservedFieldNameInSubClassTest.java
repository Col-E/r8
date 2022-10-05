// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests that a reserved field name from a given class is taken into account when renaming the
 * fields in supertypes of that class.
 */
@RunWith(Parameterized.class)
public class ReservedFieldNameInSubClassTest extends TestBase {

  private final TestParameters parameters;
  private final boolean reserveName;

  @Parameterized.Parameters(name = "{0}, reserve name: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public ReservedFieldNameInSubClassTest(TestParameters parameters, boolean reserveName) {
    this.parameters = parameters;
    this.reserveName = reserveName;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Hello world!");
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramClasses(
                TestClass.class, A.class, B.class, C.class, I.class, J.class, K.class)
            .enableMemberValuePropagationAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .addKeepMainRule(TestClass.class)
            .addKeepRules(
                reserveName
                    ? "-keepclassmembernames class "
                        + C.class.getTypeName()
                        + "{ java.lang.String a; }"
                    : "")
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), TestClass.class)
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

    FieldSubject f1FieldSubject = aClassSubject.uniqueFieldWithOriginalName("f1");
    assertThat(f1FieldSubject, isPresent());

    FieldSubject f2FieldSubject = bClassSubject.uniqueFieldWithOriginalName("f2");
    assertThat(f2FieldSubject, isPresent());

    FieldSubject f3FieldSubject = iClassSubject.uniqueFieldWithOriginalName("f3");
    assertThat(f3FieldSubject, isPresent());

    FieldSubject f4FieldSubject = jClassSubject.uniqueFieldWithOriginalName("f4");
    assertThat(f4FieldSubject, isPresent());

    FieldSubject f5FieldSubject = kClassSubject.uniqueFieldWithOriginalName("f5");
    assertThat(f5FieldSubject, isPresent());

    FieldSubject aFieldSubject = cClassSubject.uniqueFieldWithOriginalName("a");
    assertThat(aFieldSubject, isPresent());

    if (reserveName) {
      assertThat(f1FieldSubject, isPresentAndRenamed());
      assertEquals("e", f1FieldSubject.getFinalName());

      assertThat(f2FieldSubject, isPresentAndRenamed());
      assertEquals("f", f2FieldSubject.getFinalName());

      assertThat(f3FieldSubject, isPresentAndRenamed());
      assertEquals("b", f3FieldSubject.getFinalName());

      assertThat(f4FieldSubject, isPresentAndRenamed());
      assertEquals("c", f4FieldSubject.getFinalName());

      assertThat(f5FieldSubject, isPresentAndRenamed());
      assertEquals("d", f5FieldSubject.getFinalName());

      // B.a should not be renamed because it is not allowed to be minified.
      assertThat(aFieldSubject, isPresentAndNotRenamed());
    } else {
      assertThat(f1FieldSubject, isPresentAndRenamed());
      assertEquals("d", f1FieldSubject.getFinalName());

      assertThat(f2FieldSubject, isPresentAndRenamed());
      assertEquals("e", f2FieldSubject.getFinalName());

      assertThat(f3FieldSubject, isPresentAndRenamed());
      assertEquals("a", f3FieldSubject.getFinalName());

      assertThat(f4FieldSubject, isPresentAndRenamed());
      assertEquals("b", f4FieldSubject.getFinalName());

      assertThat(f5FieldSubject, isPresentAndRenamed());
      assertEquals("c", f5FieldSubject.getFinalName());

      assertThat(aFieldSubject, isPresentAndRenamed());
      assertEquals("f", aFieldSubject.getFinalName());
    }
  }

  @NoVerticalClassMerging
  static class A {

    @NeverPropagateValue String f1 = "He";
  }

  @NoVerticalClassMerging
  static class B extends A {

    @NeverPropagateValue String f2 = "l";
  }

  @NoVerticalClassMerging
  interface I {

    String f3 = System.currentTimeMillis() >= 0 ? "lo" : null;
  }

  @NoVerticalClassMerging
  interface J extends I {

    String f4 = System.currentTimeMillis() >= 0 ? " " : null;
  }

  @NoVerticalClassMerging
  interface K {

    String f5 = System.currentTimeMillis() >= 0 ? "world" : null;
  }

  static class C extends B implements J, K {

    @NeverPropagateValue String a = "!";

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
