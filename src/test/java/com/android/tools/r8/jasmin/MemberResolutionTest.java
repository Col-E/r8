// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static java.util.Collections.emptyList;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassFileVersion;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class MemberResolutionTest extends JasminTestBase {

  private static final String MAIN_CLASS = "Main";

  @Test
  public void lookupStaticFieldFromDiamondInterface() throws Exception {
    JasminBuilder builder = new JasminBuilder(ClassFileVersion.JDK_1_4);

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
    JasminBuilder builder = new JasminBuilder(ClassFileVersion.JDK_1_4);

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
  public void lookupStaticFieldWithFieldGetFromNullReference() throws Exception {
    JasminBuilder builder = new JasminBuilder(ClassFileVersion.JDK_1_4);

    ClassBuilder superClass = builder.addClass("SuperClass");
    superClass.addDefaultConstructor();
    superClass.addStaticFinalField("aField", "I", "42");

    ClassBuilder subClass = builder.addClass("SubClass", "SuperClass");
    subClass.addDefaultConstructor();

    ClassBuilder mainClass = builder.addClass(MAIN_CLASS);
    mainClass.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  aconst_null",
        "  getfield SubClass/aField I",
        "  invokevirtual java/io/PrintStream/print(I)V",
        "  return");

    ensureICCE(builder);
  }

  @Test
  public void lookupStaticFieldFromSupersInterfaceNotSupersSuper() throws Exception {
    JasminBuilder builder = new JasminBuilder(ClassFileVersion.JDK_1_4);

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

  @Test
  public void lookupStaticFieldWithShadowingInstance() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder superClass = builder.addClass("SuperClass");
    superClass.addStaticField("aField", "I", "42");
    superClass.addDefaultConstructor();

    ClassBuilder subClass = builder.addClass("SubClass", "SuperClass");
    subClass.addField("public", "aField", "I", "123");
    subClass.addDefaultConstructor();

    ClassBuilder mainClass = builder.addClass(MAIN_CLASS);
    mainClass.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  getstatic SubClass/aField I",
        "  invokevirtual java/io/PrintStream/print(I)V",
        "  return");

    ensureICCE(builder);
  }

  @Test
  @Ignore("b/69101406")
  public void lookupVirtualMethodWithConflictingPrivate() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder superClass = builder.addClass("SuperClass");
    superClass.addDefaultConstructor();
    superClass.addVirtualMethod("aMethod", emptyList(), "V",
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  bipush 42",
        "  invokevirtual java/io/PrintStream/println(I)V",
        "  return");

    ClassBuilder subClass = builder.addClass("SubClass", "SuperClass");
    subClass.addDefaultConstructor();
    subClass.addPrivateVirtualMethod("aMethod", emptyList(), "V",
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  bipush 123",
        "  invokevirtual java/io/PrintStream/println(I)V",
        "  return");

    ClassBuilder mainClass = builder.addClass(MAIN_CLASS);
    mainClass.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  new SubClass",
        "  dup",
        "  invokespecial SubClass/<init>()V",
        "  invokevirtual SubClass/aMethod()V",
        "  return");
    ensureIAEExceptJava(builder);
  }

  @Test
  @Ignore("b/69152228")
  public void lookupDirectMethodFromWrongContext() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder superClass = builder.addClass("SuperClass");
    superClass.addDefaultConstructor();
    superClass.addVirtualMethod("aMethod", emptyList(), "V",
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  bipush 42",
        "  invokevirtual java/io/PrintStream/println(I)V",
        "  return");

    ClassBuilder subClass = builder.addClass("SubClass", "SuperClass");
    subClass.addDefaultConstructor();
    subClass.addPrivateVirtualMethod("aMethod", emptyList(), "V",
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  bipush 123",
        "  invokevirtual java/io/PrintStream/println(I)V",
        "  return");

    ClassBuilder mainClass = builder.addClass(MAIN_CLASS);
    mainClass.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  new SubClass",
        "  dup",
        "  invokespecial SubClass/<init>()V",
        "  invokespecial SubClass/aMethod()V",
        "  return");
    ensureIAEExceptJava(builder);
  }

  @Test
  @Ignore("b/69101406")
  public void lookupPrivateSuperFromSubClass() throws Exception {
    JasminBuilder builder = new JasminBuilder(ClassFileVersion.JSE_5);

    ClassBuilder superClass = builder.addClass("SuperClass");
    superClass.addDefaultConstructor();
    superClass.addPrivateVirtualMethod("aMethod", emptyList(), "V",
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  bipush 42",
        "  invokevirtual java/io/PrintStream/println(I)V",
        "  return");

    ClassBuilder subClass = builder.addClass("SubClass", "SuperClass");
    subClass.addDefaultConstructor();
    subClass.addVirtualMethod("callAMethod", emptyList(), "V",
        ".limit stack 1",
        ".limit locals 1",
        "  aload 0",
        "  invokespecial SuperClass/aMethod()V",
        "  return");

    ClassBuilder mainClass = builder.addClass(MAIN_CLASS);
    mainClass.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  new SubClass",
        "  dup",
        "  invokespecial SubClass/<init>()V",
        "  invokevirtual SubClass/callAMethod()V",
        "  return");

    ensureIAEExceptJava(builder);
  }

  @Test
  @Ignore("b/69101406")
  public void lookupStaticMethodWithConflictingVirtual() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder superClass = builder.addClass("SuperClass");
    superClass.addDefaultConstructor();
    superClass.addStaticMethod("aMethod", emptyList(), "V",
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  bipush 42",
        "  invokevirtual java/io/PrintStream/println(I)V",
        "  return");

    ClassBuilder subClass = builder.addClass("SubClass", "SuperClass");
    subClass.addDefaultConstructor();
    subClass.addVirtualMethod("aMethod", emptyList(), "V",
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  bipush 123",
        "  invokevirtual java/io/PrintStream/println(I)V",
        "  return");

    ClassBuilder mainClass = builder.addClass(MAIN_CLASS);
    mainClass.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  new SubClass",
        "  dup",
        "  invokespecial SubClass/<init>()V",
        "  invokestatic SubClass/aMethod()V",
        "  return");
    ensureICCE(builder);
  }

  @Test
  @Ignore("b/69101406")
  public void lookupVirtualMethodWithConflictingStatic() throws Exception {
    JasminBuilder builder = new JasminBuilder();

    ClassBuilder superClass = builder.addClass("SuperClass");
    superClass.addDefaultConstructor();
    superClass.addVirtualMethod("aMethod", emptyList(), "V",
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  bipush 42",
        "  invokevirtual java/io/PrintStream/println(I)V",
        "  return");

    ClassBuilder subClass = builder.addClass("SubClass", "SuperClass");
    subClass.addDefaultConstructor();
    subClass.addStaticMethod("aMethod", emptyList(), "V",
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  bipush 123",
        "  invokevirtual java/io/PrintStream/println(I)V",
        "  return");

    ClassBuilder mainClass = builder.addClass(MAIN_CLASS);
    mainClass.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  new SubClass",
        "  dup",
        "  invokespecial SubClass/<init>()V",
        "  invokevirtual SubClass/aMethod()V",
        "  return");
    ensureICCE(builder);
  }

  private void ensureSameOutput(JasminBuilder app) throws Exception {
    String javaOutput = runOnJava(app, MAIN_CLASS);
    String dxOutput = runOnArtDx(app, MAIN_CLASS);
    String d8Output = runOnArtD8(app, MAIN_CLASS);
    String r8Output = runOnArtR8(app, MAIN_CLASS);
    String r8ShakenOutput = runOnArtR8(app, MAIN_CLASS, keepMainProguardConfiguration(MAIN_CLASS),
        null);
    Assert.assertEquals(javaOutput, dxOutput);
    Assert.assertEquals(javaOutput, d8Output);
    Assert.assertEquals(javaOutput, r8Output);
    Assert.assertEquals(javaOutput, r8ShakenOutput);
  }

  private void ensureICCE(JasminBuilder app) throws Exception {
    ensureRuntimeException(app, IncompatibleClassChangeError.class);
  }

  private void ensureIAEExceptJava(JasminBuilder app)
      throws Exception {
    ensureRuntimeException(app, IllegalAccessError.class);
  }

  private void ensureRuntimeException(JasminBuilder app, Class exception) throws Exception {
    String name = exception.getSimpleName();
    ProcessResult dxOutput = runOnArtDxRaw(app, MAIN_CLASS);
    Assert.assertTrue(dxOutput.stderr.contains(name));
    ProcessResult d8Output = runOnArtD8Raw(app, MAIN_CLASS);
    Assert.assertTrue(d8Output.stderr.contains(name));
    ProcessResult r8Output = runOnArtR8Raw(app, MAIN_CLASS, null);
    Assert.assertTrue(r8Output.stderr.contains(name));
    ProcessResult r8ShakenOutput = runOnArtR8Raw(app, MAIN_CLASS,
        keepMainProguardConfiguration(MAIN_CLASS), null);
    Assert.assertTrue(r8ShakenOutput.stderr.contains(name));
    ProcessResult javaOutput = runOnJavaNoVerifyRaw(app, MAIN_CLASS);
    Assert.assertTrue(javaOutput.stderr.contains(name));
  }
}
