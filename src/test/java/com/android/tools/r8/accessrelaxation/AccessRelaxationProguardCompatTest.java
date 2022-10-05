// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPrivate;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests that Proguard may change the visibility of a field or method that is explicitly kept. */
@RunWith(Parameterized.class)
public class AccessRelaxationProguardCompatTest extends TestBase {

  private static Class<?> clazz = AccessRelaxationProguardCompatTestClass.class;
  private static Class<?> clazzWithGetter = TestClassWithGetter.class;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public AccessRelaxationProguardCompatTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void r8Test() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(clazz, clazzWithGetter)
        .addKeepMainRule(clazz)
        .addKeepRules(
            "-keep class " + TestClassWithGetter.class.getTypeName() + " {",
            "  private int field;",
            "}")
        .allowAccessModification()
        .setMinApi(AndroidApiLevel.B)
        .compile()
        .inspect(inspector -> inspect(inspector, true));
  }

  @Test
  public void proguardTest() throws Exception {
    testForProguard()
        .addProgramClasses(clazz, clazzWithGetter)
        .addKeepMainRule(clazz)
        .addKeepRules(
            "-keep class " + TestClassWithGetter.class.getTypeName() + " {",
            "  private int field;",
            "}")
        .allowAccessModification()
        .compile()
        .inspect(inspector -> inspect(inspector, false));
  }

  private static void inspect(CodeInspector inspector, boolean isR8) {
    ClassSubject classSubject = inspector.clazz(clazzWithGetter);
    assertThat(classSubject, isPresent());

    FieldSubject fieldSubject = classSubject.uniqueFieldWithOriginalName("field");
    assertThat(fieldSubject, isPresent());

    // Although this field was explicitly kept, it is no longer private.
    if (isR8) {
      assertThat(fieldSubject, isPrivate());
    } else {
      assertThat(fieldSubject, not(isPrivate()));
    }
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
