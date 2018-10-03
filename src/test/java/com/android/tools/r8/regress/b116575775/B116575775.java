// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b116575775;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.ConcurrentModificationException;
import org.junit.Test;

public class B116575775 extends TestBase {

  public void runTest(Class<?> clazz) throws Exception {
    CodeInspector inspector =
        new CodeInspector(
            compileWithR8(
                readClasses(clazz),
                keepMainProguardConfiguration(clazz)));
    // Ensure toBeInlined is inlined, and only main remains.
    inspector
        .clazz(clazz)
        .forAllMethods(m -> assertEquals(m.getOriginalName(), "main"));
  }

  @Test
  public void testDuplicateGuards() throws Exception {
    runTest(B116575775Test.class);
  }

  @Test
  public void testDuplicateGuards1() throws Exception {
    runTest(B116575775Test1.class);
  }

  @Test
  public void testDuplicateGuards2() throws Exception {
    runTest(B116575775Test2.class);
  }

  @Test
  public void testDuplicateGuards3() throws Exception {
    runTest(B116575775Test3.class);
  }


  @Test
  public void byteCodeInputWithDuplicateGuards() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    clazz.addStaticMethod("toBeInlined", ImmutableList.of(), "V",
        ".limit stack 1",
        "LabelTryStart:",
        "  new java/lang/Object",
        "  invokespecial java/lang/Object/<init>()V",
        "LabelTryEnd:",
        "  goto LabelRet",
        "LabelCatch:", // Entry stack is {java/lang/Throwable}
        "  pop",
        "  goto LabelRet",
        "LabelRet:",
        "  return",
        ".catch java/lang/IllegalArgumentException from LabelTryStart to LabelTryEnd using LabelCatch",
        ".catch java/lang/ArithmeticException from LabelTryStart to LabelTryEnd using LabelCatch",
        ".catch java/lang/ArithmeticException from LabelTryStart to LabelTryEnd using LabelCatch",
        ".catch java/lang/IllegalArgumentException from LabelTryStart to LabelTryEnd using LabelCatch"
    );

    clazz.addMainMethod(
        ".limit stack 1",
        "LabelTryStart:",
        "  invokestatic Test/toBeInlined()V",
        "LabelTryEnd:",
        "  goto LabelRet",
        "LabelCatch:", // Entry stack is {java/lang/Throwable}
        "  pop",
        "  goto LabelRet",
        "LabelRet:",
        "  return",
        ".catch java/lang/ArithmeticException from LabelTryStart to LabelTryEnd using LabelCatch",
        ".catch java/lang/IllegalArgumentException from LabelTryStart to LabelTryEnd using LabelCatch",
        ".catch java/lang/ArithmeticException from LabelTryStart to LabelTryEnd using LabelCatch",
        ".catch java/lang/IllegalArgumentException from LabelTryStart to LabelTryEnd using LabelCatch");

    assertEquals(0, runOnJavaRaw("Test", builder.buildClasses(), ImmutableList.of()).exitCode);

    CodeInspector inspector =
        new CodeInspector(
            compileWithR8(
                buildAndroidApp(builder.buildClasses()),
                keepMainProguardConfiguration("Test")));
    // Ensure toBeInlined is inlined, and only main remains.
    inspector
        .clazz("Test")
        .forAllMethods(m -> assertEquals(m.getOriginalName(), "main"));
  }
}

class B116575775Test {

  public static void toBeInlined() throws ClassCastException {
    try {
      new Object();
    } catch (IllegalArgumentException | ClassCastException e) {
      System.out.println(e);
    }
  }

  public static void main(String[] args) {
    try {
      toBeInlined();
    } catch (IllegalArgumentException | ClassCastException e) {
      System.out.println(e);
    }
  }
}

class B116575775Test1 {

  public static void toBeInlined() throws ClassCastException {
    try {
      new Object();
    } catch (IllegalArgumentException | ClassCastException e) {
      System.out.println(e);
    }
  }

  public static void main(String[] args) {
    try {
      toBeInlined();
    } catch (IllegalArgumentException | ClassCastException | ArithmeticException e) {
      System.out.println(e);
    }
  }
}

class B116575775Test2 {

  public static void toBeInlined() throws ClassCastException {
    try {
      new Object();
    } catch (IllegalArgumentException | ClassCastException | ArithmeticException e) {
      System.out.println(e);
    }
  }

  public static void main(String[] args) {
    try {
      toBeInlined();
    } catch (IllegalArgumentException | ClassCastException e) {
      System.out.println(e);
    }
  }
}


class B116575775Test3 {

  public static void toBeInlined() throws ClassCastException {
    try {
      new Object();
    } catch (IllegalArgumentException | ClassCastException | ArithmeticException e) {
      System.out.println(e);
    }
  }

  public static void main(String[] args) {
    try {
      toBeInlined();
    } catch (IllegalArgumentException | ClassCastException | ConcurrentModificationException e) {
      System.out.println(e);
    }
  }
}
