// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.InvokeDirect;
import com.android.tools.r8.code.InvokeStatic;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.code.IputObject;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.code.SgetObject;
import com.android.tools.r8.code.SputObject;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.FieldSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

public class IdentifierNameStringMarkerTest extends SmaliTestBase {

  private final static String BOO = "Boo";

  @Test
  public void instancePut_singleUseOperand() throws Exception {
    SmaliBuilder builder = new SmaliBuilder("Example");
    builder.addInstanceField("aClassName", "Ljava/lang/String;");
    MethodSignature init = builder.addInitializer(ImmutableList.of(), 1,
        "invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "const-string v0, \"" + BOO + "\"",
        "iput-object v0, p0, LExample;->aClassName:Ljava/lang/String;",
        "return-void");

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class Example { java.lang.String aClassName; }",
        "-keep class Example",
        "-dontoptimize");
    Path processedApp = runR8(builder, pgConfigs);

    DexEncodedMethod method = getMethod(processedApp, init);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof InvokeDirect);
    assertTrue(code.instructions[1] instanceof ConstString);
    // TODO(b/36799092): DeadCodeRemover should be able to remove this instruction.
    ConstString constString = (ConstString) code.instructions[1];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[2] instanceof ConstString);
    constString = (ConstString) code.instructions[2];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[3] instanceof IputObject);
    assertTrue(code.instructions[4] instanceof ReturnVoid);
  }

  @Test
  public void instancePut_sharedOperand() throws Exception {
    SmaliBuilder builder = new SmaliBuilder("Example");
    builder.addInstanceField("aClassName", "Ljava/lang/String;");
    MethodSignature init = builder.addInitializer(ImmutableList.of(), 2,
        "invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "const-string v1, \"" + BOO + "\"",
        "invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "iput-object v1, p0, LExample;->aClassName:Ljava/lang/String;",
        "return-void");

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class Example { java.lang.String aClassName; }",
        "-keep class Example",
        "-dontoptimize");
    Path processedApp = runR8(builder, pgConfigs);

    DexEncodedMethod method = getMethod(processedApp, init);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof InvokeDirect);
    assertTrue(code.instructions[1] instanceof SgetObject);
    assertTrue(code.instructions[2] instanceof ConstString);
    ConstString constString = (ConstString) code.instructions[2];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[3] instanceof InvokeVirtual);
    assertTrue(code.instructions[4] instanceof ConstString);
    constString = (ConstString) code.instructions[4];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[5] instanceof IputObject);
    assertTrue(code.instructions[6] instanceof ReturnVoid);
  }

  @Test
  public void instancePut_sharedOperand_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder("Example");
    builder.addInstanceField("aClassName", "Ljava/lang/String;");
    MethodSignature init = builder.addInitializer(ImmutableList.of(), 2,
        "invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "const-string v1, \"" + BOO + "\"",
        "invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "iput-object v1, p0, LExample;->aClassName:Ljava/lang/String;",
        "return-void");
    builder.addClass(BOO);

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class Example { java.lang.String aClassName; }",
        "-keep class Example",
        "-keep,allowobfuscation class " + BOO,
        "-dontoptimize");
    Path processedApp = runR8(builder, pgConfigs);

    DexEncodedMethod method = getMethod(processedApp, init);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof InvokeDirect);
    assertTrue(code.instructions[1] instanceof SgetObject);
    assertTrue(code.instructions[2] instanceof ConstString);
    ConstString constString = (ConstString) code.instructions[2];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[3] instanceof InvokeVirtual);
    assertTrue(code.instructions[4] instanceof ConstString);
    constString = (ConstString) code.instructions[4];
    assertNotEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[5] instanceof IputObject);
    assertTrue(code.instructions[6] instanceof ReturnVoid);
  }

  @Test
  public void staticPut_singleUseOperand() throws Exception {
    SmaliBuilder builder = new SmaliBuilder("Example");
    builder.addStaticField("sClassName", "Ljava/lang/String;");
    MethodSignature clinit = builder.addStaticInitializer(1,
        "const-string v0, \"" + BOO + "\"",
        "sput-object v0, LExample;->sClassName:Ljava/lang/String;",
        "return-void");

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class Example { static java.lang.String sClassName; }",
        "-keep class Example",
        "-dontoptimize");
    Path processedApp = runR8(builder, pgConfigs);

    DexEncodedMethod method = getMethod(processedApp, clinit);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    // TODO(b/36799092): DeadCodeRemover should be able to remove this instruction.
    assertTrue(code.instructions[0] instanceof ConstString);
    ConstString constString = (ConstString) code.instructions[0];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[1] instanceof ConstString);
    constString = (ConstString) code.instructions[1];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[2] instanceof SputObject);
    assertTrue(code.instructions[3] instanceof ReturnVoid);
  }

  @Test
  public void staticPut_sharedOperand() throws Exception {
    SmaliBuilder builder = new SmaliBuilder("Example");
    builder.addStaticField("sClassName", "Ljava/lang/String;");
    MethodSignature clinit = builder.addStaticInitializer(2,
        "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "const-string v1, \"" + BOO + "\"",
        "invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "sput-object v1, LExample;->sClassName:Ljava/lang/String;",
        "return-void");

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class Example { static java.lang.String sClassName; }",
        "-keep class Example",
        "-dontoptimize");
    Path processedApp = runR8(builder, pgConfigs);

    DexEncodedMethod method = getMethod(processedApp, clinit);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof SgetObject);
    assertTrue(code.instructions[1] instanceof ConstString);
    ConstString constString = (ConstString) code.instructions[1];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[2] instanceof InvokeVirtual);
    assertTrue(code.instructions[3] instanceof ConstString);
    constString = (ConstString) code.instructions[3];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[4] instanceof SputObject);
    assertTrue(code.instructions[5] instanceof ReturnVoid);
  }

  @Test
  public void staticPut_sharedOperand_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder("Example");
    builder.addStaticField("sClassName", "Ljava/lang/String;");
    MethodSignature clinit = builder.addStaticInitializer(2,
        "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "const-string v1, \"" + BOO + "\"",
        "invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "sput-object v1, LExample;->sClassName:Ljava/lang/String;",
        "return-void");
    builder.addClass(BOO);

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class Example { static java.lang.String sClassName; }",
        "-keep class Example",
        "-keep,allowobfuscation class " + BOO,
        "-dontoptimize");
    Path processedApp = runR8(builder, pgConfigs);

    DexEncodedMethod method = getMethod(processedApp, clinit);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof SgetObject);
    assertTrue(code.instructions[1] instanceof ConstString);
    ConstString constString = (ConstString) code.instructions[1];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[2] instanceof InvokeVirtual);
    assertTrue(code.instructions[3] instanceof ConstString);
    constString = (ConstString) code.instructions[3];
    assertNotEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[4] instanceof SputObject);
    assertTrue(code.instructions[5] instanceof ReturnVoid);
  }

  @Test
  public void staticFieldWithDefaultValue() throws Exception {
    SmaliBuilder builder = new SmaliBuilder("Example");
    builder.addStaticField("sClassName", "Ljava/lang/String;", BOO);

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class Example { static java.lang.String sClassName; }",
        "-keep class Example { static java.lang.String sClassName; }",
        "-dontshrink",
        "-dontoptimize");
    Path processedApp = runR8(builder, pgConfigs);

    DexInspector inspector = new DexInspector(processedApp);

    ClassSubject clazz = inspector.clazz("Example");
    assertTrue(clazz.isPresent());
    FieldSubject field = clazz.field("java.lang.String", "sClassName");
    assertTrue(field.isPresent());
    assertTrue(field.getStaticValue() instanceof DexValueString);
    String defaultValue = ((DexValueString) field.getStaticValue()).getValue().toString();
    assertEquals(BOO, defaultValue);
  }

  @Test
  public void staticFieldWithDefaultValue_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder("Example");
    builder.addStaticField("sClassName", "Ljava/lang/String;", BOO);
    builder.addClass(BOO);

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class Example { static java.lang.String sClassName; }",
        "-keep class Example { static java.lang.String sClassName; }",
        "-keep,allowobfuscation class " + BOO,
        "-dontshrink",
        "-dontoptimize");
    Path processedApp = runR8(builder, pgConfigs);

    DexInspector inspector = new DexInspector(processedApp);

    ClassSubject clazz = inspector.clazz("Example");
    assertTrue(clazz.isPresent());
    FieldSubject field = clazz.field("java.lang.String", "sClassName");
    assertTrue(field.isPresent());
    assertTrue(field.getStaticValue() instanceof DexValueString);
    String defaultValue = ((DexValueString) field.getStaticValue()).getValue().toString();
    assertNotEquals(BOO, defaultValue);
  }

  @Test
  public void invoke_singleUseOperand() throws Exception {
    SmaliBuilder builder = new SmaliBuilder("Example");
    builder.addStaticMethod(
        "void",
        "foo",
        ImmutableList.of("java.lang.String", "java.lang.String"),
        0,
        "return-void");
    MethodSignature foo = builder.addInitializer(ImmutableList.of(), 2,
        "invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "const-string v0, \"" + BOO + "\"",
        "const-string v1, \"Mixed/form.Boo\"",
        "invoke-static {v0, v1}, LExample;->foo(Ljava/lang/String;Ljava/lang/String;)V",
        "return-void");

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class Example { static void foo(...); }",
        "-keep class Example",
        "-dontoptimize");
    Path processedApp = runR8(builder, pgConfigs);

    DexEncodedMethod method = getMethod(processedApp, foo);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof InvokeDirect);
    // TODO(b/36799092): DeadCodeRemover should be able to remove this instruction.
    assertTrue(code.instructions[1] instanceof ConstString);
    ConstString constString = (ConstString) code.instructions[1];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[2] instanceof ConstString);
    constString = (ConstString) code.instructions[2];
    assertEquals("Mixed/form.Boo", constString.getString().toString());
    assertTrue(code.instructions[3] instanceof ConstString);
    constString = (ConstString) code.instructions[3];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[4] instanceof InvokeStatic);
    assertTrue(code.instructions[5] instanceof ReturnVoid);
  }

  @Test
  public void invoke_sharedOperand() throws Exception {
    SmaliBuilder builder = new SmaliBuilder("Example");
    builder.addStaticMethod(
        "void",
        "foo",
        ImmutableList.of("java.lang.String"),
        0,
        "return-void");
    MethodSignature foo = builder.addInitializer(ImmutableList.of(), 3,
        "invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "const-string v1, \"" + BOO + "\"",
        "invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "invoke-static {v1}, LExample;->foo(Ljava/lang/String;)V",
        "return-void");

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class Example { static void foo(...); }",
        "-keep class Example",
        "-dontoptimize");
    Path processedApp = runR8(builder, pgConfigs);

    DexEncodedMethod method = getMethod(processedApp, foo);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof InvokeDirect);
    assertTrue(code.instructions[1] instanceof SgetObject);
    assertTrue(code.instructions[2] instanceof ConstString);
    ConstString constString = (ConstString) code.instructions[2];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[3] instanceof InvokeVirtual);
    assertTrue(code.instructions[4] instanceof ConstString);
    constString = (ConstString) code.instructions[4];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[5] instanceof InvokeStatic);
    assertTrue(code.instructions[6] instanceof ReturnVoid);
  }

  @Test
  public void invoke_sharedOperand_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder("Example");
    builder.addStaticMethod(
        "void",
        "foo",
        ImmutableList.of("java.lang.String"),
        0,
        "return-void");
    MethodSignature foo = builder.addInitializer(ImmutableList.of(), 3,
        "invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "const-string v1, \"" + BOO + "\"",
        "invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "invoke-static {v1}, LExample;->foo(Ljava/lang/String;)V",
        "return-void");
    builder.addClass(BOO);

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class Example { static void foo(...); }",
        "-keep class Example",
        "-keep,allowobfuscation class " + BOO,
        "-dontoptimize");
    Path processedApp = runR8(builder, pgConfigs);

    DexEncodedMethod method = getMethod(processedApp, foo);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof InvokeDirect);
    assertTrue(code.instructions[1] instanceof SgetObject);
    assertTrue(code.instructions[2] instanceof ConstString);
    ConstString constString = (ConstString) code.instructions[2];
    assertEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[3] instanceof InvokeVirtual);
    assertTrue(code.instructions[4] instanceof ConstString);
    constString = (ConstString) code.instructions[4];
    assertNotEquals(BOO, constString.getString().toString());
    assertTrue(code.instructions[5] instanceof InvokeStatic);
    assertTrue(code.instructions[6] instanceof ReturnVoid);
  }

}
