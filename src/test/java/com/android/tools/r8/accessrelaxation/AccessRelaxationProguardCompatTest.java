// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPrivate;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import org.junit.Test;

/**
 * Tests that both R8 and Proguard may change the visibility of a field or method that is explicitly
 * kept.
 */
public class AccessRelaxationProguardCompatTest extends TestBase {

  private static Class<?> clazz = AccessRelaxationProguardCompatTestClass.class;
  private static Class<?> clazzWithGetter = TestClassWithGetter.class;

  @Test
  public void r8Test() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(clazz, clazzWithGetter)
        .addKeepMainRule(clazz)
        .addKeepRules(
            "-allowaccessmodification",
            "-keep class " + TestClassWithGetter.class.getTypeName() + " {",
            "  private int field;",
            "}")
        .compile()
        .inspect(AccessRelaxationProguardCompatTest::inspect);
  }

  @Test
  public void proguardTest() throws Exception {
    testForProguard()
        .addProgramClasses(clazz, clazzWithGetter)
        .addKeepMainRule(clazz)
        .addKeepRules(
            "-allowaccessmodification",
            "-keep class " + TestClassWithGetter.class.getTypeName() + " {",
            "  private int field;",
            "}")
        .compile()
        .inspect(AccessRelaxationProguardCompatTest::inspect);
  }

  private static void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(clazzWithGetter);
    assertThat(classSubject, isPresent());

    FieldSubject fieldSubject = classSubject.uniqueFieldWithName("field");
    assertThat(fieldSubject, isPresent());

    // Although this field was explicitly kept, it is no longer private.
    assertThat(fieldSubject, not(isPrivate()));
  }
}

class AccessRelaxationProguardCompatTestClass {

  public static void main(String[] args) {
    TestClassWithGetter obj = new TestClassWithGetter();
    System.out.println(obj.get());
  }
}

class TestClassWithGetter {

  private int field = 0;

  public int get() {
    System.out.println("In method()");
    return field;
  }
}
