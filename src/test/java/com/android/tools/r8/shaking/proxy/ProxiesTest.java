// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.proxy;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.invokesuper.Consumer;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.proxy.testclasses.BaseInterface;
import com.android.tools.r8.shaking.proxy.testclasses.Main;
import com.android.tools.r8.shaking.proxy.testclasses.SubClass;
import com.android.tools.r8.shaking.proxy.testclasses.SubInterface;
import com.android.tools.r8.shaking.proxy.testclasses.TestClass;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ProxiesTest extends TestBase {
  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public ProxiesTest(Backend backend) {
    this.backend = backend;
  }

  private void runTest(List<String> additionalKeepRules, Consumer<CodeInspector> inspection,
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
    if (backend == Backend.DEX) {
      builder
          .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
          .addLibraryFiles(ToolHelper.getDefaultAndroidJar());
    } else {
      assert backend == Backend.CF;
      builder
          .setProgramConsumer(ClassFileConsumer.emptyConsumer())
          .addLibraryFiles(ToolHelper.getJava8RuntimeJar());
    }
    AndroidApp app = ToolHelper.runR8(builder.build(), o -> o.enableDevirtualization = false);
    inspection.accept(new CodeInspector(app, o -> o.enableCfFrontend = true));
    String result = backend == Backend.DEX ? runOnArt(app, mainClass) : runOnJava(app, mainClass);
    if (ToolHelper.isWindows()) {
      result = result.replace(System.lineSeparator(), "\n");
    }
    assertEquals(expectedResult, result);
  }

  private int countInstructionInX(CodeInspector inspector, Predicate<InstructionSubject> invoke) {
    MethodSignature signatureForX =
        new MethodSignature("x", "void", ImmutableList.of(BaseInterface.class.getCanonicalName()));
    MethodSubject method = inspector.clazz(Main.class).method(signatureForX);
    assert method instanceof FoundMethodSubject;
    FoundMethodSubject foundMethod = (FoundMethodSubject) method;
    return (int) Streams.stream(foundMethod.iterateInstructions(invoke)).count();
  }

  private int countInstructionInY(CodeInspector inspector, Predicate<InstructionSubject> invoke) {
    MethodSignature signatureForY =
        new MethodSignature("y", "void", ImmutableList.of(SubInterface.class.getCanonicalName()));
    MethodSubject method = inspector.clazz(Main.class).method(signatureForY);
    assert method instanceof FoundMethodSubject;
    FoundMethodSubject foundMethod = (FoundMethodSubject) method;
    return (int)
        Streams.stream(foundMethod.iterateInstructions(invoke))
            .filter(
                instruction -> {
                  InvokeInstructionSubject invokeInstruction =
                      (InvokeInstructionSubject) instruction;
                  return invokeInstruction.invokedMethod().qualifiedName().endsWith("method");
                })
            .count();
  }

  private int countInstructionInZ(CodeInspector inspector, Predicate<InstructionSubject> invoke) {
    MethodSignature signatureForZ =
        new MethodSignature("z", "void", ImmutableList.of(TestClass.class.getCanonicalName()));
    MethodSubject method = inspector.clazz(Main.class).method(signatureForZ);
    assert method instanceof FoundMethodSubject;
    FoundMethodSubject foundMethod = (FoundMethodSubject) method;
    return (int)
        Streams.stream(foundMethod.iterateInstructions(invoke))
            .filter(
                instruction -> {
                  InvokeInstructionSubject invokeInstruction =
                      (InvokeInstructionSubject) instruction;
                  return invokeInstruction.invokedMethod().qualifiedName().endsWith("method");
                })
            .count();
  }

  private int countInstructionInZSubClass(
      CodeInspector inspector, Predicate<InstructionSubject> invoke) {
    MethodSignature signatureForZ =
        new MethodSignature("z", "void", ImmutableList.of(SubClass.class.getCanonicalName()));
    MethodSubject method = inspector.clazz(Main.class).method(signatureForZ);
    assert method instanceof FoundMethodSubject;
    FoundMethodSubject foundMethod = (FoundMethodSubject) method;
    return (int)
        Streams.stream(foundMethod.iterateInstructions(invoke))
            .filter(
                instruction -> {
                  InvokeInstructionSubject invokeInstruction =
                      (InvokeInstructionSubject) instruction;
                  return invokeInstruction.invokedMethod().qualifiedName().endsWith("method");
                })
            .count();
  }

  private void noInterfaceKept(CodeInspector inspector) {
    // Indirectly assert that method is inlined into x, y and z.
    assertEquals(1, countInstructionInX(inspector, InstructionSubject::isInvokeInterface));
    assertEquals(1, countInstructionInY(inspector, InstructionSubject::isInvokeInterface));
    assertEquals(1, countInstructionInZ(inspector, InstructionSubject::isInvokeVirtual));
  }

  @Test
  public void testNoInterfaceKept() throws Exception {
    runTest(ImmutableList.of(),
        this::noInterfaceKept,
        "TestClass 1\nTestClass 1\nTestClass 1\nProxy\nEXCEPTION\n");
  }

  private void baseInterfaceKept(CodeInspector inspector) {
    // Indirectly assert that method is not inlined into x.
    assertEquals(3, countInstructionInX(inspector, InstructionSubject::isInvokeInterface));
    // Indirectly assert that method is inlined into y and z.
    assertEquals(1, countInstructionInY(inspector, InstructionSubject::isInvokeInterface));
    assertEquals(1, countInstructionInZ(inspector, InstructionSubject::isInvokeVirtual));
    assertEquals(1, countInstructionInZSubClass(inspector, InstructionSubject::isInvokeVirtual));
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

  private void subInterfaceKept(CodeInspector inspector) {
    // Indirectly assert that method is not inlined into x or y.
    assertEquals(3, countInstructionInX(inspector, InstructionSubject::isInvokeInterface));
    assertEquals(3, countInstructionInY(inspector, InstructionSubject::isInvokeInterface));
    // Indirectly assert that method is inlined into z.
    assertEquals(1, countInstructionInZ(inspector, InstructionSubject::isInvokeVirtual));
    assertEquals(1, countInstructionInZSubClass(inspector, InstructionSubject::isInvokeVirtual));
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

  private void classKept(CodeInspector inspector) {
    // Indirectly assert that method is not inlined into x, y or z.
    assertEquals(3, countInstructionInX(inspector, InstructionSubject::isInvokeInterface));
    assertEquals(3, countInstructionInY(inspector, InstructionSubject::isInvokeInterface));
    assertEquals(3, countInstructionInZ(inspector, InstructionSubject::isInvokeVirtual));
    assertEquals(3, countInstructionInZSubClass(inspector, InstructionSubject::isInvokeVirtual));
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