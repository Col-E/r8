// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.keepparameternames;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.LocalVariableTable;
import com.android.tools.r8.utils.codeinspector.LocalVariableTable.LocalVariableTableEntry;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.TypeSubject;
import java.lang.reflect.Method;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class KeepParameterNamesUnsortedLocalVariablesTableTest extends TestBase implements Opcodes {

  private final TestParameters parameters;
  private final boolean keepParameterNames;

  @Parameterized.Parameters(name = "{0}, keepparameternames {1}")
  public static Collection<Object[]> data() {
    return buildParameters(getTestParameters().withCfRuntimes().build(), BooleanUtils.values());
  }

  public KeepParameterNamesUnsortedLocalVariablesTableTest(
      TestParameters parameters, boolean keepParameterNames) {
    this.parameters = parameters;
    this.keepParameterNames = keepParameterNames;
  }

  private void checkLocalVariable(
      LocalVariableTableEntry localVariable,
      int index,
      String name,
      TypeSubject type,
      String signature) {
    assertEquals(index, localVariable.index);
    assertEquals(name, localVariable.name);
    assertEquals(type, localVariable.type);
    assertEquals(signature, localVariable.signature);
  }

  private void checkLocalVariableTable(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz("Api");
    assertThat(classSubject, isPresent());

    MethodSubject method = classSubject.uniqueMethodWithName("api1");
    assertThat(method, isPresent());

    assertEquals(keepParameterNames, method.hasLocalVariableTable());
    if (keepParameterNames) {
      LocalVariableTable localVariableTable = method.getLocalVariableTable();
      assertEquals(3, localVariableTable.size());
      checkLocalVariable(
          localVariableTable.get(0),
          0,
          "this",
          classSubject.asFoundClassSubject().asTypeSubject(),
          null);
      checkLocalVariable(
          localVariableTable.get(1), 1, "parameter1", inspector.getTypeSubject("int"), null);
      checkLocalVariable(
          localVariableTable.get(2), 2, "parameter2", inspector.getTypeSubject("int"), null);
    } else {
      method.getLocalVariableTable().isEmpty();
    }
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("In Api.api1 6", "Result 6");
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(KeepParameterNamesUnsortedLocalVariablesTableTest.class)
        .addProgramClassFileData(dumpApi())
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-keep class Api { api*(...); }")
        .apply(this::configureKeepParameterNames)
        .compile()
        .inspect(this::checkLocalVariableTable)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  private void configureKeepParameterNames(TestShrinkerBuilder builder) {
    if (keepParameterNames) {
      builder.addKeepRules("-keepparameternames");
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      Class<?> api = Class.forName("Api");
      Method api1 = api.getMethod("api1", int.class, int.class);
      System.out.println("Result " + api1.invoke(api.getConstructor().newInstance(), 2, 3));
    }
  }

  /*

  Dump below is a modified version of this java code:

  public class Api {
    public int api1(int parameter1, int parameter2) {
      int x = parameter1 * parameter2;
      System.out.println("In Api.api1 " + x);
      return x;
    }
  }

  Modifications:
    LabelX introduced to stop parameter1 and parameter2.
    The local variable x stored into slot 1 from LabelX (using parameter1's slot)
    The order of the local variable table changed to have x before parameter1.
  */

  public static byte[] dumpApi() throws Exception {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(V1_8, ACC_SUPER | ACC_PUBLIC, "Api", null, "java/lang/Object", null);

    classWriter.visitSource("Api.java", null);

    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(184, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable("this", "LApi;", null, label0, label1, 0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "api1", "(II)I", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(186, label0);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitVarInsn(ILOAD, 2);
      methodVisitor.visitInsn(IMUL);
      Label labelX = new Label();
      methodVisitor.visitLabel(labelX);
      methodVisitor.visitVarInsn(ISTORE, 1);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(187, label1);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
      methodVisitor.visitLdcInsn("In Api.api1 ");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(I)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(188, label2);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitInsn(IRETURN);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLocalVariable("this", "LApi;", null, label0, label3, 0);
      methodVisitor.visitLocalVariable("x", "I", null, labelX, label3, 1);
      methodVisitor.visitLocalVariable("parameter1", "I", null, label0, labelX, 1);
      methodVisitor.visitLocalVariable("parameter2", "I", null, label0, labelX, 2);
      methodVisitor.visitMaxs(3, 3);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
