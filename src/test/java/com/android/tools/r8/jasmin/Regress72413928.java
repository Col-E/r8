// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.jasmin.JasminBuilder.ClassFileVersion;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Type;

@RunWith(Parameterized.class)
public class Regress72413928 extends JasminTestBase {

  public Regress72413928(Type type, boolean supportedOnD8AndDx) {
    this.type = type;
    this.supportedOnD8AndDx = supportedOnD8AndDx;
  }

  @Parameters(name = "{0}")
  public static List<Object[]> getData() {
    return ImmutableList.copyOf(new Object[][]{
        new Object[]{Type.INT_TYPE, true},
        new Object[]{Type.SHORT_TYPE, false},
        new Object[]{Type.CHAR_TYPE, false},
        new Object[]{Type.BYTE_TYPE, true},
    });
  }

  private final Type type;
  private final boolean supportedOnD8AndDx;

  private JasminBuilder buildClass(Type type) {
    JasminBuilder builder = new JasminBuilder(ClassFileVersion.JDK_1_4);
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    String conversion;
    switch (type.getSort()) {
      case Type.INT:
        conversion = "";
        break;
      case Type.SHORT:
        conversion = "i2s";
        break;
      case Type.CHAR:
        conversion = "i2c";
        break;
      case Type.BYTE:
        conversion = "i2b";
        break;
      default:
        throw new Unreachable();
    }

    clazz.addStaticMethod("returnsConstantFalse", ImmutableList.of(), "Z",
        ".limit stack 10",
        ".limit locals 10",
        "  iconst_0",
        conversion,
        "  ireturn");

    clazz.addStaticMethod("returnsConstantTrue", ImmutableList.of(), "Z",
        ".limit stack 10",
        ".limit locals 10",
        "  iconst_1",
        conversion,
        "  ireturn");

    clazz.addStaticMethod("returnsArgument", ImmutableList.of(type.getDescriptor()), "Z",
        ".limit stack 10",
        ".limit locals 10",
        "  iload_0",
        conversion,
        "  ireturn");

    // public static void main(String args[]) {
    //   System.out.println(Test.returnsConstantFalse());
    //   System.out.println(Test.returnsConstantFalse());
    //   System.out.println(Test.returnsArgument(0));
    //   System.out.println(Test.returnsArgument(1));
    // }
    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 10",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  invokestatic Test/returnsConstantFalse()Z",
        "  invokevirtual java/io/PrintStream/println(Z)V",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  invokestatic Test/returnsConstantTrue()Z",
        "  invokevirtual java/io/PrintStream/println(Z)V",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  iconst_0",
        "  invokestatic Test/returnsArgument(" + type.getDescriptor() + ")Z",
        "  invokevirtual java/io/PrintStream/println(Z)V",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  iconst_1",
        "  invokestatic Test/returnsArgument(" + type.getDescriptor() + ")Z",
        "  invokevirtual java/io/PrintStream/println(Z)V",
        "  return");
    return builder;
  }

  @Test
  public void test() throws Exception {
    JasminBuilder builder = buildClass(type);
    String javaResult = runOnJava(builder, "Test");
    ProcessResult d8Result = runOnArtD8Raw(builder, "Test");
    ProcessResult dxResult = runOnArtDxRaw(builder, "Test");
    if (supportedOnD8AndDx) {
      assertEquals(0, d8Result.exitCode);
      assertEquals(javaResult, d8Result.stdout);
      assertEquals(0, dxResult.exitCode);
      assertEquals(javaResult, dxResult.stdout);
    } else {
      assertNotEquals(0, d8Result.exitCode);
      assertNotEquals(0, dxResult.exitCode);
    }
  }
}
