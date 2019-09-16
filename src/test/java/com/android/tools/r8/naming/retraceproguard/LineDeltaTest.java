// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retraceproguard;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ForceInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;

public class LineDeltaTest extends TestBase {
  public String runTest(Backend backend) throws Exception {
    return testForR8(backend)
        .enableForceInliningAnnotations()
        .addProgramClasses(LineDeltaTestClass.class)
        .addKeepMainRule(LineDeltaTestClass.class)
        .addKeepRules("-keepattributes LineNumberTable")
        .run(LineDeltaTestClass.class)
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
  }

  private long mapLines(String map) {
    return StringUtils.splitLines(map).stream().filter(line -> !line.startsWith("#")).count();
  }

  @Test
  public void testDex() throws Exception {
    assertEquals(17, mapLines(runTest(Backend.DEX)));
  }

  @Test
  public void testCf() throws Exception {
    assertEquals(5, mapLines(runTest(Backend.CF)));
  }
}

class LineDeltaTestClass {
  @ForceInline
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

  @ForceInline
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
