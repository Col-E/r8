// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.proguard.configuration;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;

public class ProguardRuleWithEllipsisForReturnTypeTest extends TestBase {

  private static final Class<?> clazz = ProguardRuleWithEllipsisForReturnTypeTestClass.class;
  private static final String expectedOutput = StringUtils.lines("Hello world!");

  @Test
  public void testR8() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(clazz)
        .addKeepRules(
            "-keep class " + clazz.getTypeName() + " {",
            "  private static ... unused;",
            "  public static ... main(...);",
            "}")
        .run(clazz)
        .assertSuccessWithOutput(expectedOutput)
        .inspect(this::inspect);
  }

  @Test
  public void testProguard() throws Exception {
    testForProguard()
        .addProgramClasses(clazz)
        .addKeepRules(
            "-keep class " + clazz.getTypeName() + " {",
            "  private static ... unused;",
            "  public static ... main(...);",
            "}")
        .run(clazz)
        .assertSuccessWithOutput(expectedOutput)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(clazz);
    assertThat(classSubject, isPresent());
    assertThat(classSubject.uniqueFieldWithName("unused"), isPresent());
    assertThat(classSubject.uniqueMethodWithName("main"), isPresent());
  }
}

class ProguardRuleWithEllipsisForReturnTypeTestClass {

  private static Object unused = new Object();

  public static void main(String[] args) {
    System.out.println("Hello world!");
  }
}
