// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;

public class StaticFieldPropagationTest extends TestBase {

  @Test
  public void test() throws Exception {
    String expectedOutput =
        StringUtils.lines("TestClass: Hel", "TestClass: lo", "TestClass: wor", "TestClass: ld!");

    R8TestRunResult result =
        testForR8(Backend.DEX)
            .addProgramClasses(TestClass.class, Log.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .noMinification()
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput);

    CodeInspector inspector = result.inspector();

    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());

    MethodSubject clinitMethodSubject = classSubject.clinit();
    assertThat(clinitMethodSubject, not(isPresent()));

    MethodSubject mainMethodSubject = classSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());

    // Verify that all static-get instructions have been removed.
    assertTrue(mainMethodSubject.streamInstructions().noneMatch(InstructionSubject::isStaticGet));
  }
}

class TestClass {

  private static String TAG_1 = "TestClass";
  private static String TAG_2;
  private static String TAG_3;
  private static String TAG_4;

  static {
    TAG_2 = TestClass.class.getSimpleName();
    TAG_3 = getConstantString();
    TAG_4 = getConstantString();
  }

  public static void main(String[] args) {
    Log.d(TAG_1, "Hel");
    Log.d(TAG_2, "lo");
    Log.d(TAG_3, "wor");
    Log.d(TAG_4, "ld!");
  }

  private static String getConstantString() {
    return "TestClass";
  }
}

class Log {

  @NeverInline
  public static void d(String tag, String message) {
    System.out.println(tag + ": " + message);
  }
}
