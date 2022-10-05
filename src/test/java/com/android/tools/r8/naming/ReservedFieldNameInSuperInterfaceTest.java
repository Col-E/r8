// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.NeverPropagateValue;
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
 * Tests that a reserved field name from a given interface I is taken into account when renaming the
 * fields in subclasses of I.
 */
@RunWith(Parameterized.class)
public class ReservedFieldNameInSuperInterfaceTest extends TestBase {

  private final boolean reserveName;

  @Parameterized.Parameters(name = "Reserve name: {0}")
  public static Boolean[] data() {
    return BooleanUtils.values();
  }

  public ReservedFieldNameInSuperInterfaceTest(boolean reserveName) {
    this.reserveName = reserveName;
  }

  @Test
  public void testProgramField() throws Exception {
    String expectedOutput = StringUtils.lines("Hello world!");
    R8TestRunResult result =
        testForR8(Backend.DEX)
            .addProgramClasses(TestClass.class, A.class, I.class, J.class)
            .addKeepMainRule(TestClass.class)
            .addKeepRules(
                reserveName
                    ? "-keepclassmembernames class " + I.class.getTypeName() + "{ <fields>; }"
                    : "")
            .enableMemberValuePropagationAnnotations()
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput);

    CodeInspector inspector = result.inspector();

    ClassSubject iClassSubject = inspector.clazz(I.class);
    assertThat(iClassSubject, isPresent());

    FieldSubject aFieldSubject = iClassSubject.uniqueFieldWithOriginalName("a");
    assertThat(aFieldSubject, isPresent());

    // Interface fields are visited/renamed before fields on classes. Thus, the interface field I.a
    // will be visited first and assigned the name a. As it ends up receiving the same name as in
    // the input program, it has not technically been renamed.
    assertThat(aFieldSubject, isPresentAndNotRenamed());

    inspect(inspector);
  }

  @Test
  public void testLibraryField() throws Exception {
    assumeFalse("No need to add keep rules for the library", reserveName);

    String expectedOutput = StringUtils.lines("Hello world!");
    testForR8(Backend.DEX)
        .addProgramClasses(TestClass.class, A.class, J.class)
        .addLibraryClasses(I.class)
        .addLibraryFiles(runtimeJar(Backend.DEX))
        .addKeepMainRule(TestClass.class)
        .enableMemberValuePropagationAnnotations()
        .compile()
        .addRunClasspathFiles(testForD8().addProgramClasses(I.class).compile().writeToZip())
        .run(TestClass.class)
        .assertSuccessWithOutput(expectedOutput)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject jClassSubject = inspector.clazz(J.class);
    assertThat(jClassSubject, isPresent());

    FieldSubject f1FieldSubject = jClassSubject.uniqueFieldWithOriginalName("f1");
    assertThat(f1FieldSubject, isPresent());
    assertThat(f1FieldSubject, isPresentAndRenamed());
    assertEquals("b", f1FieldSubject.getFinalName());

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    FieldSubject f2FieldSubject = aClassSubject.uniqueFieldWithOriginalName("f2");
    assertThat(f2FieldSubject, isPresent());
    assertThat(f2FieldSubject, isPresentAndRenamed());
    assertEquals("c", f2FieldSubject.getFinalName());
  }

  interface I {

    String a = System.currentTimeMillis() >= 0 ? "Hello" : null;
  }

  interface J extends I {

    String f1 = System.currentTimeMillis() >= 0 ? " " : null;
  }

  static class A implements I {

    @NeverPropagateValue String f2 = "world!";

    @Override
    public String toString() {
      return a + J.f1 + f2;
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new A());
    }
  }
}
