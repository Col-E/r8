// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class MemberResolutionTest extends JasminTestBase {

  private static final String MAIN_CLASS = "Main";

  @Test
  public void lookupStaticFieldFromDiamondInterface() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder interfaceA = builder.addInterface("InterfaceA");
    interfaceA.addStaticFinalField("aField", "I", "42");

    ClassBuilder interfaceB = builder.addInterface("InterfaceB");
    interfaceB.addStaticFinalField("aField", "I", "123");

    builder.addInterface("SubInterface", "InterfaceB", "InterfaceA");

    ClassBuilder mainClass = builder.addClass(MAIN_CLASS);
    mainClass.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  getstatic SubInterface/aField I",
        "  invokevirtual java/io/PrintStream/print(I)V",
        "  return");

    ensureSameOutput(builder);
  }

  @Test
  public void lookupStaticFieldFromInterfaceNotSuper() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder superClass = builder.addClass("SuperClass");
    superClass.addStaticFinalField("aField", "I", "42");

    ClassBuilder iface = builder.addInterface("Interface");
    iface.addStaticFinalField("aField", "I", "123");

    builder.addClass("SubClass", "SuperClass", "Interface");

    ClassBuilder mainClass = builder.addClass(MAIN_CLASS);
    mainClass.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  getstatic SubClass/aField I",
        "  invokevirtual java/io/PrintStream/print(I)V",
        "  return");

    ensureSameOutput(builder);
  }

  @Test
  public void lookupStaticFieldFromSupersInterfaceNotSupersSuper() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder superSuperClass = builder.addClass("SuperSuperClass");
    superSuperClass.addStaticFinalField("aField", "I", "123");

    ClassBuilder iface = builder.addInterface("Interface");
    iface.addStaticFinalField("aField", "I", "42");

    builder.addClass("SuperClass", "SuperSuperClass", "Interface");

    builder.addClass("SubClass", "SuperClass");

    ClassBuilder mainClass = builder.addClass(MAIN_CLASS);
    mainClass.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  getstatic SubClass/aField I",
        "  invokevirtual java/io/PrintStream/print(I)V",
        "  return");

    ensureSameOutput(builder);
  }

  @Test
  @Ignore("b/69101406")
  public void lookupInstanceFieldWithShadowingStatic() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder superClass = builder.addClass("SuperClass");
    superClass.addField("public", "aField", "I", "42");
    superClass.addDefaultConstructor();

    ClassBuilder interfaceA = builder.addInterface("Interface");
    interfaceA.addStaticFinalField("aField", "I", "123");

    ClassBuilder subClass = builder.addClass("SubClass", "SuperClass", "Interface");
    subClass.addDefaultConstructor();

    ClassBuilder mainClass = builder.addClass(MAIN_CLASS);
    mainClass.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  new SubClass",
        "  dup",
        "  invokespecial SubClass/<init>()V",
        "  getfield SubClass/aField I",
        "  invokevirtual java/io/PrintStream/print(I)V",
        "  return");

    ensureICCE(builder);
  }


  private void ensureSameOutput(JasminBuilder app) throws Exception {
    String dxOutput = runOnArtDx(app, MAIN_CLASS);
    String d8Output = runOnArtD8(app, MAIN_CLASS);
    Assert.assertEquals(dxOutput, d8Output);
    String r8Output = runOnArtR8(app, MAIN_CLASS);
    Assert.assertEquals(dxOutput, r8Output);
    String javaOutput = runOnJava(app, MAIN_CLASS);
    Assert.assertEquals(javaOutput, r8Output);
  }

  private void ensureICCE(JasminBuilder app) throws Exception {
    ProcessResult dxOutput = runOnArtDxRaw(app, MAIN_CLASS);
    Assert.assertTrue(dxOutput.stderr.contains("IncompatibleClassChangeError"));
    ProcessResult d8Output = runOnArtD8Raw(app, MAIN_CLASS);
    Assert.assertTrue(d8Output.stderr.contains("IncompatibleClassChangeError"));
    ProcessResult r8Output = runOnArtR8Raw(app, MAIN_CLASS, null);
    Assert.assertTrue(r8Output.stderr.contains("IncompatibleClassChangeError"));
    ProcessResult javaOutput = runOnJavaRaw(app, MAIN_CLASS);
    Assert.assertTrue(javaOutput.stderr.contains("IncompatibleClassChangeError"));
  }
}
