// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.proxy;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.code.InvokeInterface;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.invokesuper.Consumer;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.proxy.testclasses.BaseInterface;
import com.android.tools.r8.shaking.proxy.testclasses.Main;
import com.android.tools.r8.shaking.proxy.testclasses.SubClass;
import com.android.tools.r8.shaking.proxy.testclasses.SubInterface;
import com.android.tools.r8.shaking.proxy.testclasses.TestClass;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ProxiesTest extends TestBase {

  private void runTest(List<String> additionalKeepRules, Consumer<DexInspector> inspection,
      String expectedResult)
      throws Exception {
    Class mainClass = Main.class;
    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(ToolHelper.getClassFilesForTestPackage(mainClass.getPackage()));
    builder.addProguardConfiguration(ImmutableList.of(
        "-keep class " + mainClass.getCanonicalName() + " {",
        // Keep x, y and z to avoid them being inlined into main.
        "  private void x(com.android.tools.r8.shaking.proxy.testclasses.BaseInterface);",
        "  private void y(com.android.tools.r8.shaking.proxy.testclasses.SubInterface);",
        "  private void z(com.android.tools.r8.shaking.proxy.testclasses.TestClass);",
        "  private void z(com.android.tools.r8.shaking.proxy.testclasses.SubClass);",
        "  public static void main(java.lang.String[]);",
        "}",
        "-dontobfuscate"),
        Origin.unknown()
    );
    builder.addProguardConfiguration(additionalKeepRules, Origin.unknown());
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    AndroidApp app = ToolHelper.runR8(builder.build());
    inspection.accept(new DexInspector(app));
    assertEquals(expectedResult, runOnArt(app, mainClass));
  }

  private int countInvokeInterfaceInX(DexInspector inspector) {
    MethodSignature signatureForX =
        new MethodSignature("x", "void", ImmutableList.of(BaseInterface.class.getCanonicalName()));
    DexCode x = inspector.clazz(Main.class).method(signatureForX).getMethod().getCode().asDexCode();
    return (int) Arrays.stream(x.instructions)
        .filter(instruction -> instruction instanceof InvokeInterface)
        .count();
  }

  private int countInvokeInterfaceInY(DexInspector inspector) {
    MethodSignature signatureForY =
        new MethodSignature("y", "void", ImmutableList.of(SubInterface.class.getCanonicalName()));
    DexCode y = inspector.clazz(Main.class).method(signatureForY).getMethod().getCode().asDexCode();
    return (int) Arrays.stream(y.instructions)
        .filter(instruction -> instruction instanceof InvokeInterface)
        .map(instruction -> (InvokeInterface) instruction)
        .filter(instruction -> instruction.getMethod().qualifiedName().endsWith("method"))
        .count();
  }

  private int countInvokeVirtualInZ(DexInspector inspector) {
    MethodSignature signatureForZ =
        new MethodSignature("z", "void", ImmutableList.of(TestClass.class.getCanonicalName()));
    DexCode z = inspector.clazz(Main.class).method(signatureForZ).getMethod().getCode().asDexCode();
    return (int) Arrays.stream(z.instructions)
        .filter(instruction -> instruction instanceof InvokeVirtual)
        .map(instruction -> (InvokeVirtual) instruction)
        .filter(instruction -> instruction.getMethod().qualifiedName().endsWith("method"))
        .count();
  }

  private int countInvokeVirtualInZSubClass(DexInspector inspector) {
    MethodSignature signatureForZ =
        new MethodSignature("z", "void", ImmutableList.of(SubClass.class.getCanonicalName()));
    DexCode z = inspector.clazz(Main.class).method(signatureForZ).getMethod().getCode().asDexCode();
    return (int) Arrays.stream(z.instructions)
        .filter(instruction -> instruction instanceof InvokeVirtual)
        .map(instruction -> (InvokeVirtual) instruction)
        .filter(instruction -> instruction.getMethod().qualifiedName().endsWith("method"))
        .count();
  }

  private void noInterfaceKept(DexInspector inspector) {
    // Indirectly assert that method is inlined into x, y and z.
    assertEquals(1, countInvokeInterfaceInX(inspector));
    assertEquals(1, countInvokeInterfaceInY(inspector));
    assertEquals(1, countInvokeVirtualInZ(inspector));
  }

  @Test
  public void testNoInterfaceKept() throws Exception {
    runTest(ImmutableList.of(),
        this::noInterfaceKept,
        "TestClass 1\nTestClass 1\nTestClass 1\nProxy\nEXCEPTION\n");
  }

  private void baseInterfaceKept(DexInspector inspector) {
    // Indirectly assert that method is not inlined into x.
    assertEquals(3, countInvokeInterfaceInX(inspector));
    // Indirectly assert that method is inlined into y and z.
    assertEquals(1, countInvokeInterfaceInY(inspector));
    assertEquals(1, countInvokeVirtualInZ(inspector));
    assertEquals(1, countInvokeVirtualInZSubClass(inspector));
  }

  @Test
  public void testBaseInterfaceKept() throws Exception {
    runTest(ImmutableList.of(
        "-keep interface " + BaseInterface.class.getCanonicalName() + " {",
        "  <methods>;",
        "}"),
        this::baseInterfaceKept,
        "TestClass 1\nTestClass 1\nTestClass 1\nProxy\nProxy\nProxy\n" +
        "TestClass 2\nTestClass 2\nTestClass 2\nProxy\nEXCEPTION\n");
  }

  private void subInterfaceKept(DexInspector inspector) {
    // Indirectly assert that method is not inlined into x or y.
    assertEquals(3, countInvokeInterfaceInX(inspector));
    assertEquals(3, countInvokeInterfaceInY(inspector));
    // Indirectly assert that method is inlined into z.
    assertEquals(1, countInvokeVirtualInZ(inspector));
    assertEquals(1, countInvokeVirtualInZSubClass(inspector));
  }

  @Test
  public void testSubInterfaceKept() throws Exception {
    runTest(ImmutableList.of(
        "-keep interface " + SubInterface.class.getCanonicalName() + " {",
        "  <methods>;",
        "}"),
        this::subInterfaceKept,
        "TestClass 1\nTestClass 1\nTestClass 1\nProxy\nProxy\nProxy\n" +
        "TestClass 2\nTestClass 2\nTestClass 2\nProxy\nProxy\nProxy\n" +
        "TestClass 3\nTestClass 3\nTestClass 3\n" +
        "TestClass 4\nTestClass 4\nTestClass 4\nSUCCESS\n");
  }

  private void classKept(DexInspector inspector) {
    // Indirectly assert that method is not inlined into x, y or z.
    assertEquals(3, countInvokeInterfaceInX(inspector));
    assertEquals(3, countInvokeInterfaceInY(inspector));
    assertEquals(3, countInvokeVirtualInZ(inspector));
    assertEquals(3, countInvokeVirtualInZSubClass(inspector));
  }

  @Test
  public void testClassKept() throws Exception {
    runTest(ImmutableList.of(
        "-keep class " + TestClass.class.getCanonicalName() + " {",
        "  <methods>;",
        "}"),
        this::classKept,
        "TestClass 1\nTestClass 1\nTestClass 1\nProxy\nProxy\nProxy\n" +
        "TestClass 2\nTestClass 2\nTestClass 2\nProxy\nProxy\nProxy\n" +
        "TestClass 3\nTestClass 3\nTestClass 3\n" +
        "TestClass 4\nTestClass 4\nTestClass 4\nSUCCESS\n");
  }

  @Test
  public void testSubClassKept() throws Exception {
    runTest(ImmutableList.of(
        "-keep class " + SubClass.class.getCanonicalName() + " {",
        "  <methods>;",
        "}"),
        this::classKept,
        "TestClass 1\nTestClass 1\nTestClass 1\nProxy\nProxy\nProxy\n" +
        "TestClass 2\nTestClass 2\nTestClass 2\nProxy\nProxy\nProxy\n" +
        "TestClass 3\nTestClass 3\nTestClass 3\n" +
        "TestClass 4\nTestClass 4\nTestClass 4\nSUCCESS\n");
  }
}