// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.R8TestRunResult;
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
 * Tests that a reserved field name from an interface I, which is implemented by a subclass B, is
 * taken into account when renaming the fields in a superclass of B.
 */
@RunWith(Parameterized.class)
public class ReservedFieldNameInSubInterfaceTest extends TestBase {

  private final boolean reserveName;

  @Parameterized.Parameters(name = "Reserve name: {0}")
  public static Boolean[] data() {
    return BooleanUtils.values();
  }

  public ReservedFieldNameInSubInterfaceTest(boolean reserveName) {
    this.reserveName = reserveName;
  }

  @Test
  public void testProgramField() throws Exception {
    String expectedOutput = StringUtils.lines("Hello world!");
    R8TestRunResult result =
        testForR8(Backend.DEX)
            .addProgramClasses(TestClass.class, A.class, B.class, I.class, J.class)
            .enableMergeAnnotations()
            .addKeepMainRule(TestClass.class)
            .addKeepRules(
                reserveName
                    ? "-keepclassmembernames class "
                        + J.class.getTypeName()
                        + "{ java.lang.String a; }"
                    : "")
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput);

    CodeInspector inspector = result.inspector();

    ClassSubject iClassSubject = inspector.clazz(I.class);
    assertThat(iClassSubject, isPresent());

    ClassSubject jClassSubject = inspector.clazz(J.class);
    assertThat(jClassSubject, isPresent());

    FieldSubject f1FieldSubject = iClassSubject.uniqueFieldWithName("f1");
    assertThat(f1FieldSubject, isPresent());
    assertThat(f1FieldSubject, isRenamed());

    FieldSubject aFieldSubject = jClassSubject.uniqueFieldWithName("a");
    assertThat(aFieldSubject, isPresent());

    if (reserveName) {
      assertThat(aFieldSubject, not(isRenamed()));

      // Interface fields are visited/renamed before fields on classes. Thus, the interface field
      // I.f1 will be visited first and assigned the name b (since the name a is reserved).
      assertEquals("b", f1FieldSubject.getFinalName());

    } else {
      // Interface fields are visited/renamed before fields on classes. Thus, the interface field
      // I.f1 will be visited first and assigned the name a.
      assertEquals("a", f1FieldSubject.getFinalName());

      // The interface field J.a will be visited after I.f1, and will therefore be assigned the name
      // b.
      assertThat(aFieldSubject, isRenamed());
      assertEquals("b", aFieldSubject.getFinalName());
    }

    inspect(inspector);
  }

  @Test
  public void testLibraryField() throws Exception {
    assumeFalse("No need to add keep rules for the library", reserveName);

    String expectedOutput = StringUtils.lines("Hello world!");
    testForR8(Backend.DEX)
        .addProgramClasses(TestClass.class, A.class, B.class)
        .addLibraryClasses(I.class, J.class)
        .enableMergeAnnotations()
        .addKeepMainRule(TestClass.class)
        .compile()
        .addRunClasspathFiles(
            testForD8().addProgramClasses(I.class, J.class).compile().writeToZip())
        .run(TestClass.class)
        .assertSuccessWithOutput(expectedOutput)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    FieldSubject f2FieldSubject = aClassSubject.uniqueFieldWithName("f2");
    assertThat(f2FieldSubject, isPresent());
    assertThat(f2FieldSubject, isRenamed());

    // TODO(b/128973195): A.f2 should be renamed to c.
    assertEquals("a", f2FieldSubject.getFinalName());
  }

  @NeverMerge
  interface I {

    String f1 = System.currentTimeMillis() >= 0 ? "Hello" : null;
  }

  @NeverMerge
  interface J extends I {

    String a = System.currentTimeMillis() >= 0 ? "world!" : null;
  }

  @NeverMerge
  static class A {

    String f2 = " ";
  }

  static class B extends A implements J {

    @Override
    public String toString() {
      return f1 + f2 + a;
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new B());
    }
  }
}
