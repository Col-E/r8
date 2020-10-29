// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.NoHorizontalClassMerging;
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
 * Tests that a reserved field name from a given class A is taken into account when renaming the
 * fields in subclasses of A.
 */
@RunWith(Parameterized.class)
public class ReservedFieldNameInSuperClassTest extends TestBase {

  private final boolean reserveName;

  @Parameterized.Parameters(name = "Reserve name: {0}")
  public static Boolean[] data() {
    return BooleanUtils.values();
  }

  public ReservedFieldNameInSuperClassTest(boolean reserveName) {
    this.reserveName = reserveName;
  }

  @Test
  public void testProgramClass() throws Exception {
    String expectedOutput = StringUtils.lines("A.a", "ASub1.foo", "ASub2.bar");
    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addProgramClasses(A.class, ASub1.class, ASub2.class, TestClass.class)
            .addKeepMainRule(TestClass.class)
            .addKeepRules(
                reserveName
                    ? "-keepclassmembernames class " + A.class.getTypeName() + "{ <fields>; }"
                    : "")
            .enableNoHorizontalClassMergingAnnotations()
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject testClassForFieldSubject = inspector.clazz(A.class);
    assertThat(testClassForFieldSubject, isPresent());

    FieldSubject aFieldSubject = testClassForFieldSubject.uniqueFieldWithName("a");
    assertThat(aFieldSubject, isPresent());

    // Fields are visited/renamed according to the class hierarchy order. Thus, the field A.a will
    // be visited first and assigned the name a. As it ends up receiving the same name as in the
    // original program, it has not technically been renamed.
    assertThat(aFieldSubject, isPresentAndNotRenamed());

    inspect(inspector);
  }

  @Test
  public void testLibraryClass() throws Exception {
    assumeFalse("No need to add keep rules for the library", reserveName);

    String expectedOutput = StringUtils.lines("A.a", "ASub1.foo", "ASub2.bar");
    testForR8(Backend.DEX)
        .addProgramClasses(ASub1.class, ASub2.class, TestClass.class)
        .addLibraryClasses(A.class)
        .addLibraryFiles(runtimeJar(Backend.DEX))
        .addKeepMainRule(TestClass.class)
        .enableNoHorizontalClassMergingAnnotations()
        .compile()
        .addRunClasspathFiles(testForD8().addProgramClasses(A.class).compile().writeToZip())
        .run(TestClass.class)
        .assertSuccessWithOutput(expectedOutput)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject aSub1ClassSubject = inspector.clazz(ASub1.class);
    assertThat(aSub1ClassSubject, isPresent());

    FieldSubject fooFieldSubject = aSub1ClassSubject.uniqueFieldWithName("foo");
    assertThat(fooFieldSubject, isPresentAndRenamed());
    assertNotEquals("a", fooFieldSubject.getFinalName());

    ClassSubject aSub2ClassSubject = inspector.clazz(ASub2.class);
    assertThat(aSub2ClassSubject, isPresent());

    FieldSubject barFieldSubject = aSub2ClassSubject.uniqueFieldWithName("bar");
    assertThat(barFieldSubject, isPresentAndRenamed());
    assertNotEquals("a", barFieldSubject.getFinalName());

    // Verify that ASub1.foo and ASub2.bar has been renamed to the same name.
    //
    // Note that this is not a requirement for the correctness of the field name minifier. The field
    // name minifier is in principle free to choose different names for ASub1.foo and ASub2.bar, but
    // this would have a negative impact on code size.
    assertEquals(fooFieldSubject.getFinalName(), barFieldSubject.getFinalName());
  }

  static class A {

    static String a;
  }

  static class ASub1 extends A {

    static String foo;

    ASub1() {
      a = "A.a";
      foo = "ASub1.foo";
    }

    @Override
    public String toString() {
      return a + System.lineSeparator() + foo;
    }
  }

  @NoHorizontalClassMerging
  static class ASub2 extends A {

    static String bar;

    ASub2() {
      bar = "ASub2.bar";
    }

    @Override
    public String toString() {
      return bar;
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new ASub1());
      System.out.println(new ASub2());
    }
  }
}
