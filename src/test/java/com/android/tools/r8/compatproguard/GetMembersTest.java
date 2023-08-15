// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compatproguard;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.dex.code.DexAputObject;
import com.android.tools.r8.dex.code.DexConst4;
import com.android.tools.r8.dex.code.DexConstClass;
import com.android.tools.r8.dex.code.DexConstString;
import com.android.tools.r8.dex.code.DexFilledNewArray;
import com.android.tools.r8.dex.code.DexInvokeVirtual;
import com.android.tools.r8.dex.code.DexMoveResultObject;
import com.android.tools.r8.dex.code.DexNewArray;
import com.android.tools.r8.dex.code.DexReturnVoid;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;

public class GetMembersTest extends CompatProguardSmaliTestBase {

  private final String CLASS_NAME = "Example";
  private final static String BOO = "Boo";

  @Test
  public void getField_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addMainMethod(
        2,
        "const-class v0, LBoo;",
        "const-string v1, \"foo\"",
        "invoke-virtual {v0, v1}, "
            + "Ljava/lang/Class;->getField(Ljava/lang/String;)Ljava/lang/reflect/Field;",
        "return-void");

    builder.addClass(BOO);
    builder.addStaticField("foo", "Ljava/lang/String;");

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        "-dontshrink",
        "-dontoptimize");
    CodeInspector inspector = runCompatProguard(builder, pgConfigs);

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    MethodSubject method = clazz.method(CodeInspector.MAIN);
    assertTrue(method.isPresent());

    DexCode code = method.getMethod().getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof DexConstClass);
    assertTrue(code.instructions[1] instanceof DexConstString);
    DexConstString constString = (DexConstString) code.instructions[1];
    assertNotEquals("foo", constString.getString().toString());
    assertTrue(code.instructions[2] instanceof DexInvokeVirtual);
    assertTrue(code.instructions[3] instanceof DexReturnVoid);
  }

  @Test
  public void getMethod_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addMainMethod(
        3,
        "const-class v0, Ljava/lang/String;",
        "const/4 v2, 0x1",
        "new-array v2, v2, [Ljava/lang/Class;",
        "const/4 v1, 0x0",
        "aput-object v0, v2, v1",
        "const-class v0, LBoo;",
        "const-string v1, \"foo\"",
        "invoke-virtual {v0, v1, v2}, "
            + "Ljava/lang/Class;->getMethod(Ljava/lang/String;[Ljava/lang/Class;)"
            + "Ljava/lang/reflect/Method;",
        "return-void");

    builder.addClass(BOO);
    builder.addStaticMethod("void", "foo", ImmutableList.of("java.lang.String"), 0, "return-void");

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        "-dontshrink",
        "-dontoptimize");
    CodeInspector inspector = runCompatProguard(builder, pgConfigs);

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    MethodSubject method = clazz.method(CodeInspector.MAIN);
    assertTrue(method.isPresent());

    DexCode code = method.getMethod().getCode().asDexCode();

    // Accept either array construction style (differs based on minSdkVersion).
    if (code.instructions[1] instanceof DexFilledNewArray) {
      assertTrue(code.instructions[0] instanceof DexConstClass);
      assertTrue(code.instructions[1] instanceof DexFilledNewArray);
      assertTrue(code.instructions[2] instanceof DexMoveResultObject);
      assertTrue(code.instructions[3] instanceof DexConstClass);
      assertTrue(code.instructions[4] instanceof DexConstString);
      assertNotEquals("foo", code.instructions[4].asConstString().getString().toString());
      assertTrue(code.instructions[5] instanceof DexInvokeVirtual);
      assertTrue(code.instructions[6] instanceof DexReturnVoid);
    } else {
      assertTrue(code.instructions[0] instanceof DexConstClass);
      assertTrue(code.instructions[1] instanceof DexConst4);
      assertTrue(code.instructions[2] instanceof DexNewArray);
      assertTrue(code.instructions[3] instanceof DexConst4);
      assertTrue(code.instructions[4] instanceof DexAputObject);
      assertTrue(code.instructions[5] instanceof DexConstClass);
      assertTrue(code.instructions[6] instanceof DexConstString);
      assertNotEquals("foo", code.instructions[6].asConstString().getString().toString());
      assertTrue(code.instructions[7] instanceof DexInvokeVirtual);
      assertTrue(code.instructions[8] instanceof DexReturnVoid);
    }
  }

}
