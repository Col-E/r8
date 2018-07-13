// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compatproguard;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.code.ConstClass;
import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.InvokeStatic;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.dexinspector.ClassSubject;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.android.tools.r8.utils.dexinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;

public class AtomicFieldUpdaterTest extends CompatProguardSmaliTestBase {

  private final String CLASS_NAME = "Example";
  private final static String BOO = "Boo";

  @Test
  public void integerFieldUpdater_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addMainMethod(
        2,
        "const-class v0, LBoo;",
        "const-string v1, \"foo\"",
        "invoke-static {v0, v1}, "
            + "Ljava/util/concurrent/atomic/AtomicIntegerFieldUpdater;"
            + "->newUpdater(Ljava/lang/Class;Ljava/lang/String;)"
            + "Ljava/util/concurrent/atomic/AtomicIntegerFieldUpdater;",
        "return-void");

    builder.addClass(BOO);
    builder.addStaticField("foo", "I");

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        "-dontshrink",
        "-dontoptimize");
    DexInspector inspector = runCompatProguard(builder, pgConfigs);

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    MethodSubject method = clazz.method(DexInspector.MAIN);
    assertTrue(method.isPresent());

    DexCode code = method.getMethod().getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof ConstClass);
    assertTrue(code.instructions[1] instanceof ConstString);
    ConstString constString = (ConstString) code.instructions[1];
    assertNotEquals("foo", constString.getString().toString());
    assertTrue(code.instructions[2] instanceof InvokeStatic);
    assertTrue(code.instructions[3] instanceof ReturnVoid);
  }

  @Test
  public void longFieldUpdater_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addMainMethod(
        2,
        "const-class v0, LBoo;",
        "const-string v1, \"foo\"",
        "invoke-static {v0, v1}, "
            + "Ljava/util/concurrent/atomic/AtomicLongFieldUpdater;"
            + "->newUpdater(Ljava/lang/Class;Ljava/lang/String;)"
            + "Ljava/util/concurrent/atomic/AtomicLongFieldUpdater;",
        "return-void");

    builder.addClass(BOO);
    builder.addStaticField("foo", "J");

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        "-dontshrink",
        "-dontoptimize");
    DexInspector inspector = runCompatProguard(builder, pgConfigs);

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    MethodSubject method = clazz.method(DexInspector.MAIN);
    assertTrue(method.isPresent());

    DexCode code = method.getMethod().getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof ConstClass);
    assertTrue(code.instructions[1] instanceof ConstString);
    ConstString constString = (ConstString) code.instructions[1];
    assertNotEquals("foo", constString.getString().toString());
    assertTrue(code.instructions[2] instanceof InvokeStatic);
    assertTrue(code.instructions[3] instanceof ReturnVoid);
  }

  @Test
  public void referenceFieldUpdater_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addMainMethod(
        3,
        "const-class v0, LBoo;",
        "const-class v1, Ljava/lang/Object;",
        "const-string v2, \"foo\"",
        "invoke-static {v0, v1, v2}, "
            + "Ljava/util/concurrent/atomic/AtomicReferenceFieldUpdater;"
            + "->newUpdater(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/String;)"
            + "Ljava/util/concurrent/atomic/AtomicReferenceFieldUpdater;",
        "return-void");

    builder.addClass(BOO);
    builder.addStaticField("foo", "Ljava/lang/Object;");

    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        "-dontshrink",
        "-dontoptimize");
    DexInspector inspector = runCompatProguard(builder, pgConfigs);

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    MethodSubject method = clazz.method(DexInspector.MAIN);
    assertTrue(method.isPresent());

    DexCode code = method.getMethod().getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof ConstClass);
    assertTrue(code.instructions[1] instanceof ConstClass);
    assertTrue(code.instructions[2] instanceof ConstString);
    ConstString constString = (ConstString) code.instructions[2];
    assertNotEquals("foo", constString.getString().toString());
    assertTrue(code.instructions[3] instanceof InvokeStatic);
    assertTrue(code.instructions[4] instanceof ReturnVoid);
  }
}
