// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retraceproguard;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LineDeltaTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    String proguardMap =
        testForR8(parameters.getBackend())
            .addProgramClasses(LineDeltaTestClass.class)
            .addKeepMainRule(LineDeltaTestClass.class)
            .addKeepRules("-keepattributes LineNumberTable")
            .setMinApi(parameters)
            .compile()
            .inspect(
                inspector ->
                    assertEquals(1, inspector.clazz(LineDeltaTestClass.class).allMethods().size()))
            .run(parameters.getRuntime(), LineDeltaTestClass.class)
            .assertSuccessWithOutput(
                StringUtils.lines(
                    "In test1() - 1",
                    "In test1() - 2",
                    "In test1() - 3",
                    "In test1() - 4",
                    "In test2() - 1",
                    "In test2() - 2",
                    "In test2() - 3",
                    "In test2() - 4"))
            .proguardMap();
    assertEquals(parameters.isCfRuntime() ? 5 : 17, mapLines(proguardMap));
  }

  private long mapLines(String map) {
    return StringUtils.splitLines(map).stream().filter(line -> !line.startsWith("#")).count();
  }
}

class LineDeltaTestClass {
  static void test1() {
    System.out.println("In test1() - 1");
    // One line comment.
    System.out.println("In test1() - 2");
    // Two line comments.
    //
    System.out.println("In test1() - 3");
    // Four line comments.
    //
    //
    //
    System.out.println("In test1() - 4");
  }

  static void test2() {
    System.out.println("In test2() - 1");
    // Seven line comments.
    //
    //
    //
    //
    //
    //
    System.out.println("In test2() - 2");
    // Eight line comments.
    //
    //
    //
    //
    //
    //
    //
    System.out.println("In test2() - 3");
    // Nine line comments.
    //
    //
    //
    //
    //
    //
    //
    //
    System.out.println("In test2() - 4");
  }

  public static void main(String[] args) {
    test1();
    test2();
  }
}
