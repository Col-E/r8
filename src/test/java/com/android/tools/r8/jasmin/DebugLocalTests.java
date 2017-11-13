// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.debuginfo.DebugInfoInspector;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.jasmin.JasminBuilder.ClassFileVersion;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class DebugLocalTests extends JasminTestBase {

  @Test
  public void testSwap() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");
    MethodSignature foo = clazz.addVirtualMethod("foo", ImmutableList.of("Ljava/lang/String;"), "V",
        // The first three vars are out-of-order to verify that the order is not relied on.
        ".var 5 is t I from L4 to L6",
        ".var 1 is bar Ljava/lang/String; from L0 to L9",
        ".var 0 is this LTest; from L0 to L9",
        ".var 2 is x I from L1 to L9",
        ".var 3 is y I from L2 to L9",
        ".var 4 is z I from L3 to L9",
        ".var 5 is foobar Ljava/lang/String; from L7 to L9",
        ".limit locals 6",
        ".limit stack 2",
        "L0:",
        ".line 23",
        " iconst_1",
        " istore 2",
        "L1:",
        ".line 24",
        " iconst_2",
        " istore 3",
        "L2:",
        ".line 25",
        " iconst_3",
        " istore 4",
        "L3:",
        " .line 27",
        " iload 3",
        " istore 5",
        "L4:",
        " .line 28",
        " iload 2",
        " istore 3",
        "L5:",
        " .line 29",
        " iload 5",
        " istore 2",
        "L6:",
        " .line 32",
        " new java/lang/StringBuilder",
        " dup",
        " invokespecial java/lang/StringBuilder/<init>()V",
        " ldc \"And the value of y is: \"",
        " invokevirtual java/lang/StringBuilder/append(Ljava/lang/String;)Ljava/lang/StringBuilder;",
        " iload 2",
        " invokevirtual java/lang/StringBuilder/append(I)Ljava/lang/StringBuilder;",
        " iload 3",
        " invokevirtual java/lang/StringBuilder/append(I)Ljava/lang/StringBuilder;",
        " iload 4",
        " invokevirtual java/lang/StringBuilder/append(I)Ljava/lang/StringBuilder;",
        " invokevirtual java/lang/StringBuilder/toString()Ljava/lang/String;",
        " astore 5",
        "L7:",
        " .line 34",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  aload 5",
        "  invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "L8:",
        " .line 35",
        " return",
        "L9:");

    clazz.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "  new Test",
        "  dup",
        "  invokespecial Test/<init>()V",
        "  ldc \"Fsg\"",
        "  invokevirtual Test/foo(Ljava/lang/String;)V",
        "  return");

    String expected = "And the value of y is: 213";
    String javaResult = runOnJava(builder, clazz.name);
    assertEquals(expected, javaResult);

    AndroidApp jasminApp = builder.build();
    AndroidApp d8App = ToolHelper.runD8(jasminApp);

    DexInspector inspector = new DexInspector(d8App);
    ClassSubject classSubject = inspector.clazz("Test");
    MethodSubject methodSubject = classSubject.method(foo);
    DexCode code = methodSubject.getMethod().getCode().asDexCode();
    DexDebugInfo info = code.getDebugInfo();
    assertEquals(23, info.startLine);
    assertEquals(1, info.parameters.length);
    assertEquals("bar", info.parameters[0].toString());

    // TODO(zerny): Verify the debug computed locals information.

    String artResult = runOnArt(d8App, clazz.name);
    assertEquals(expected, artResult);
  }

  @Test
  public void testNoLocalInfoOnStack() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");
    MethodSignature foo = clazz.addVirtualMethod("foo", ImmutableList.of(), "I",
        ".var 0 is this LTest; from Init to End",
        ".var 1 is x I from XStart to XEnd",
        ".limit locals 2",
        ".limit stack 1",
        "Init:",
        ".line 1",
        "  ldc 0",
        "  istore 1",
        "XStart:",
        ".line 2",
        "  ldc 42",
        "  istore 1",
        "  iload 1",
        "XEnd:",
        ".line 3",
        "  ireturn",
        "End:");

    clazz.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "  new Test",
        "  dup",
        "  invokespecial Test/<init>()V",
        "  invokevirtual Test/foo()I",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  swap",
        "  invokevirtual java/io/PrintStream/print(I)V",
        "  return");

    String expected = "42";
    String javaResult = runOnJava(builder, clazz.name);
    assertEquals(expected, javaResult);

    AndroidApp jasminApp = builder.build();
    AndroidApp d8App = ToolHelper.runD8(jasminApp);

    DebugInfoInspector info = new DebugInfoInspector(d8App, clazz.name, foo);
    info.checkStartLine(1);
    info.checkLineHasExactLocals(1, "this", "Test");
    info.checkLineHasExactLocals(2, "this", "Test", "x", "int");
    info.checkLineHasExactLocals(3, "this", "Test");

    String artResult = runOnArt(d8App, clazz.name);
    assertEquals(expected, artResult);
  }

  // Check that we properly handle switching a local slot from one variable to another.
  @Test
  public void checkLocalChange() throws Exception {
    JasminBuilder builder = new JasminBuilder(ClassFileVersion.JDK_1_4);
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    MethodSignature foo = clazz.addStaticMethod("foo", ImmutableList.of("I"), "I",
        ".limit stack 2",
        ".limit locals 2",
        ".var 0 is param I from MethodStart to MethodEnd",
        ".var 1 is x I from LabelXStart to LabelXEnd",
        ".var 1 is y I from LabelYStart to LabelYEnd",

        "MethodStart:",
        ".line 1",

        "  ldc 0",
        "  istore 1",
        "LabelXStart:",
        ".line 2",
        "  invokestatic Test/ensureLine()V",
        "LabelXEnd:",

        "  iload 0",
        "  lookupswitch",
        "  1: Case1",
        "  default: CaseDefault",

        "Case1:",
        ".line 3",
        "  ldc 42",
        "  istore 1",
        "LabelYStart:",
        ".line 4",
        "  invokestatic Test/ensureLine()V",
        "  goto AfterSwitch",

        "CaseDefault:",
        ".line 5",
        "  ldc -42",
        "  istore 1",
        ".line 6",
        "  invokestatic Test/ensureLine()V",

        "AfterSwitch:",
        ".line 7",
        "  iload 1",
        "  ireturn",
        "LabelYEnd:",

        "MethodEnd:"
    );

    clazz.addStaticMethod("ensureLine", ImmutableList.of(), "V",
        ".limit stack 0",
        ".limit locals 0",
        "  return");

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  ldc 0",
        "  invokestatic Test/foo(I)I",
        "  ldc 1",
        "  invokestatic Test/foo(I)I",
        "  pop",
        "  return");

    String expected = "";
    String javaResult = runOnJava(builder, clazz.name);
    assertEquals(expected, javaResult);

    AndroidApp jasminApp = builder.build();
    AndroidApp d8App = ToolHelper.runD8(jasminApp);
    String artResult = runOnArt(d8App, clazz.name);
    assertEquals(expected, artResult);

    DebugInfoInspector info = new DebugInfoInspector(d8App, clazz.name, foo);
    info.checkStartLine(1);
    info.checkLineHasExactLocals(1, "param", "int");
    info.checkLineHasExactLocals(2, "param", "int", "x", "int");
    info.checkLineHasExactLocals(4, "param", "int", "y", "int");
    info.checkLineHasExactLocals(6, "param", "int", "y", "int");
    info.checkLineHasExactLocals(7, "param", "int", "y", "int");
  }

  @Test
  public void testLocalManyRanges() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    MethodSignature foo = clazz.addStaticMethod("foo", ImmutableList.of("I"), "I",
        ".limit stack 2",
        ".limit locals 2",
        ".var 0 is param I from Init to End",
        ".var 1 is x I from LabelStart1 to LabelEnd1",
        ".var 1 is x I from LabelStart2 to LabelEnd2",
        ".var 1 is x I from LabelStart3 to LabelEnd3",
        ".var 1 is x I from LabelStart4 to LabelEnd4",
        "Init:",
        ".line 1",
        "  iload 0",
        "  istore 1",

        "LabelStart1:",
        ".line 2",
        "  invokestatic Test/ensureLine()V",
        "LabelEnd1:",
        ".line 3",
        "  invokestatic Test/ensureLine()V",

        "LabelStart2:",
        ".line 4",
        "  invokestatic Test/ensureLine()V",
        "LabelEnd2:",
        ".line 5",
        "  invokestatic Test/ensureLine()V",

        "LabelStart3:",
        ".line 6",
        "  invokestatic Test/ensureLine()V",
        "LabelEnd3:",
        ".line 7",
        "  invokestatic Test/ensureLine()V",

        "LabelStart4:",
        ".line 8",
        "  invokestatic Test/ensureLine()V",
        "LabelEnd4:",
        ".line 9",
        "  invokestatic Test/ensureLine()V",
        "  iload 1",
        "  ireturn",
        "End:"
    );

    clazz.addStaticMethod("ensureLine", ImmutableList.of(), "V",
        ".limit stack 0",
        ".limit locals 0",
        "  return");

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  ldc 42",
        "  invokestatic Test/foo(I)I",
        "  invokevirtual java/io/PrintStream/print(I)V",
        "  return");

    String expected = "42";
    String javaResult = runOnJava(builder, clazz.name);
    assertEquals(expected, javaResult);

    AndroidApp jasminApp = builder.build();
    AndroidApp d8App = ToolHelper.runD8(jasminApp);
    String artResult = runOnArt(d8App, clazz.name);
    assertEquals(expected, artResult);

    DebugInfoInspector info = new DebugInfoInspector(d8App, clazz.name, foo);
    info.checkStartLine(1);
    info.checkLineHasExactLocals(1, "param", "int");
    info.checkLineHasExactLocals(2, "param", "int", "x", "int");
    info.checkLineHasExactLocals(3, "param", "int");
    info.checkLineHasExactLocals(4, "param", "int", "x", "int");
    info.checkLineHasExactLocals(5, "param", "int");
    info.checkLineHasExactLocals(6, "param", "int", "x", "int");
    info.checkLineHasExactLocals(7, "param", "int");
    info.checkLineHasExactLocals(8, "param", "int", "x", "int");
    info.checkLineHasExactLocals(9, "param", "int");
  }

  @Test
  public void argumentLiveAtReturn() throws Exception {
    JasminBuilder builder = new JasminBuilder(ClassFileVersion.JDK_1_4);
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    /*
     This is the original Java source code.

     public static int argumentLiveAtReturn(int x) {  // Line 1
       switch (x) {
         case 0:
           return 0;
         case 1:
           return 0;
         case 2:
           return 0;
         case 100:
           return 1;
         case 101:
           return 1;
         case 102:
           return 1;
       }
       return -1;
     }
   */
    MethodSignature foo = clazz.addStaticMethod("argumentLiveAtReturn", ImmutableList.of("I"), "I",
        ".limit stack 2",
        ".limit locals 1",
        ".var 0 is x I from L0 to L8",
        "L0:",
        ".line 2",
        "  iload 0",
        "lookupswitch",
        "  0: L1",
        "  1: L2",
        "  2: L3",
        "  100: L4",
        "  101: L5",
        "  102: L6",
        "  default: L7",
        "L1:",
        ".line 4",
        "  iconst_0",
        "  ireturn",
        "L2:",
        ".line 6",
        "  iconst_0",
        "  ireturn",
        "L3:",
        ".line 8",
        "  iconst_0",
        "  ireturn",
        "L4:",
        ".line 10",
        "  iconst_1",
        "  ireturn",
        "L5:",
        ".line 12",
        "  iconst_1",
        "  ireturn",
        "L6:",
        ".line 14",
        "  iconst_1",
        "  ireturn",
        "L7:",
        ".line 16",
        "  iconst_m1",
        "  ireturn",
        "L8:"
    );

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  ldc -1",
        "  invokestatic Test/argumentLiveAtReturn(I)I",
        "  invokevirtual java/io/PrintStream/print(I)V",
        "  return");

    String expected = "-1";
    String javaResult = runOnJava(builder, clazz.name);
    assertEquals(expected, javaResult);

    AndroidApp jasminApp = builder.build();
    AndroidApp d8App = ToolHelper.runD8(jasminApp);
    String artResult = runOnArt(d8App, clazz.name);
    assertEquals(expected, artResult);
    DebugInfoInspector info = new DebugInfoInspector(d8App, clazz.name, foo);
    info.checkStartLine(2);
    info.checkLineHasExactLocals(2, "x", "int");
    info.checkLineHasExactLocals(4, "x", "int");
    info.checkLineHasExactLocals(6, "x", "int");
    info.checkLineHasExactLocals(8, "x", "int");
    info.checkLineHasExactLocals(10, "x", "int");
    info.checkLineHasExactLocals(12, "x", "int");
    info.checkLineHasExactLocals(14, "x", "int");
    info.checkLineHasExactLocals(16, "x", "int");
  }

  @Test
  public void testLocalSwitchRewriteToIfs() throws Exception {
    JasminBuilder builder = new JasminBuilder(ClassFileVersion.JDK_1_4);
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    /*
      This is the original Java source code. The code generated by javac have been
      slightly modified below to end local t on the lookupswitch instruction.

      public static int switchRewrite(int x) {  // Line 1
        {
          int t = x + 1;
          x = t;
          x = x + x;
        }
        switch (x) {
          case 1:
            return 7;
          case 100:
            return 8;
        }
        return -1;
      }
    */
    MethodSignature foo = clazz.addStaticMethod("switchRewrite", ImmutableList.of("I"), "I",
        ".limit stack 2",
        ".limit locals 2",
        ".var 0 is x I from L0 to L7",
        ".var 1 is t I from L1 to L3",

        "L0:",
        ".line 3",
        "  iload 0",
        "  iconst_1",
        "  iadd",
        "  istore 1",
        "L1:",
        ".line 4",
        "  iload 1",
        "  istore 0",
        "L2:",
        ".line 5",
        "  iload 0",
        "  iload 0",
        "  iadd",
        "  istore 0",
        "L3_ORIGINAL:",  // This is where javac normally ends t.
        ".line 7",
        "  iload 0",
        "lookupswitch",
        "  0: L4",
        "  100: L5",
        "  default: L6",
        "L3:",           // Moved L3 here to end t on the switch instruction.
        "L4:",
        ".line 9",
        "  iconst_0",
        "  ireturn",
        "L5:",
        ".line 11",
        "  iconst_1",
        "  ireturn",
        "L6:",
        ".line 13",
        "  iconst_m1",
        "  ireturn",
        "L7:"
    );

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  ldc 1",
        "  invokestatic Test/switchRewrite(I)I",
        "  invokevirtual java/io/PrintStream/print(I)V",
        "  return");

    String expected = "-1";
    String javaResult = runOnJava(builder, clazz.name);
    assertEquals(expected, javaResult);

    AndroidApp jasminApp = builder.build();
    AndroidApp d8App = ToolHelper.runD8(jasminApp);
    String artResult = runOnArt(d8App, clazz.name);
    assertEquals(expected, artResult);

    DebugInfoInspector info = new DebugInfoInspector(d8App, clazz.name, foo);
    info.checkStartLine(3);
    info.checkLineHasExactLocals(3, "x", "int");
    info.checkLineHasExactLocals(4, "x", "int", "t", "int");
    info.checkLineHasExactLocals(5, "x", "int", "t", "int");
    info.checkLineHasExactLocals(7, "x", "int", "t", "int");
    info.checkLineHasExactLocals(9, "x", "int");
    info.checkLineHasExactLocals(11, "x", "int");
    info.checkLineHasExactLocals(13, "x", "int");
  }

  @Test
  public void testLocalSwitchRewriteToSwitches() throws Exception {
    JasminBuilder builder = new JasminBuilder(ClassFileVersion.JDK_1_4);
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    /*
      This is the original Java source code. The code generated by javac have been
      slightly modified below to end local t on the lookupswitch instruction.

      public static int switchRewrite(int x) {  // Line 1
        {
          int t = x + 1;
          x = t;
          x = x + x;
        }
        switch (x) {
          case 0:
            return 0;
          case 1:
            return 0;
          case 2:
            return 0;
          case 100:
            return 1;
          case 101:
            return 1;
          case 102:
            return 1;
        }
        return -1;
      }
    */
    MethodSignature foo = clazz.addStaticMethod("switchRewrite", ImmutableList.of("I"), "I",
        ".limit stack 2",
        ".limit locals 2",
        ".var 0 is x I from L0 to L11",
        ".var 1 is t I from L1 to L3",

        "L0:",
        ".line 3",
        "  iload 0",
        "  iconst_1",
        "  iadd",
        "  istore 1",
        "L1:",
        ".line 4",
        "  iload 1",
        "  istore 0",
        "L2:",
        ".line 5",
        "  iload 0",
        "  iload 0",
        "  iadd",
        "  istore 0",
        "L3_ORIGINAL:",  // This is where javac normally ends t.
        ".line 7",
        "  iload 0",
        "lookupswitch",
        "  0: L4",
        "  1: L5",
        "  2: L6",
        "  100: L7",
        "  101: L8",
        "  102: L9",
        "  default: L10",
        "L3:",           // Moved L3 here to end t on the switch instruction.
        "L4:",
        ".line 9",
        "  iconst_0",
        "  ireturn",
        "L5:",
        ".line 11",
        "  iconst_0",
        "  ireturn",
        "L6:",
        ".line 13",
        "  iconst_0",
        "  ireturn",
        "L7:",
        ".line 15",
        "  iconst_1",
        "  ireturn",
        "L8:",
        ".line 17",
        "  iconst_1",
        "  ireturn",
        "L9:",
        ".line 19",
        "  iconst_1",
        "  ireturn",
        "L10:",
        ".line 21",
        "  iconst_m1",
        "  ireturn",
        "L11:"
    );

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  ldc 1",
        "  invokestatic Test/switchRewrite(I)I",
        "  invokevirtual java/io/PrintStream/print(I)V",
        "  return");

    String expected = "-1";
    String javaResult = runOnJava(builder, clazz.name);
    assertEquals(expected, javaResult);

    AndroidApp jasminApp = builder.build();
    AndroidApp d8App = ToolHelper.runD8(jasminApp);
    String artResult = runOnArt(d8App, clazz.name);
    assertEquals(expected, artResult);

    DebugInfoInspector info = new DebugInfoInspector(d8App, clazz.name, foo);
    info.checkStartLine(3);
    info.checkLineHasExactLocals(3, "x", "int");
    info.checkLineHasExactLocals(4, "x", "int", "t", "int");
    info.checkLineHasExactLocals(5, "x", "int", "t", "int");
    info.checkLineHasExactLocals(7, "x", "int", "t", "int");
    info.checkLineHasExactLocals(9, "x", "int");
    info.checkLineHasExactLocals(11, "x", "int");
    info.checkLineHasExactLocals(13, "x", "int");
  }

  @Test
  public void testLocalEndAfterLine() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");
    MethodSignature foo = clazz.addStaticMethod("foo", ImmutableList.of("I"), "I",
        ".limit stack 2",
        ".limit locals 2",
        ".var 0 is x I from L0 to L4",
        ".var 1 is t I from L1 to L3",
        "L0:",
        ".line 1",
        "  iload 0",
        "  iconst_1",
        "  iadd",
        "  istore 1",
        "L1:",
        ".line 2",
        "  iload 1",
        "  istore 0",
        "L2:",
        ".line 3",
        "  iload 0",
        "  iload 0",
        "  iadd",
        "  istore 0",
        ".line 4",
        "  iload 0",
        "L3:", // This should not end t on line 4!
        ".line 5",
        "  ireturn",
        "L4:"
    );

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  ldc 1",
        "  invokestatic Test/foo(I)I",
        "  invokevirtual java/io/PrintStream/print(I)V",
        "  return");

    String expected = "4";
    String javaResult = runOnJava(builder, clazz.name);
    assertEquals(expected, javaResult);

    AndroidApp jasminApp = builder.build();
    AndroidApp d8App = ToolHelper.runD8(jasminApp);
    String artResult = runOnArt(d8App, clazz.name);
    assertEquals(expected, artResult);

    DebugInfoInspector info = new DebugInfoInspector(d8App, clazz.name, foo);
    info.checkStartLine(1);
    info.checkLineHasExactLocals(1, "x", "int");
    info.checkLineHasExactLocals(2, "x", "int", "t", "int");
    info.checkLineHasExactLocals(3, "x", "int", "t", "int");
    info.checkLineHasExactLocals(4, "x", "int", "t", "int");
    info.checkLineHasExactLocals(5, "x", "int");
  }
}
