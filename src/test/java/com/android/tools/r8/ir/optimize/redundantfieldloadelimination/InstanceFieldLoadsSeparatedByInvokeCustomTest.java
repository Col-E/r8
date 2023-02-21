// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.redundantfieldloadelimination;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class InstanceFieldLoadsSeparatedByInvokeCustomTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimes()
        .withDexRuntimesStartingFromIncluding(Version.V8_1_0)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.O)
        .build();
  }

  public InstanceFieldLoadsSeparatedByInvokeCustomTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(InstanceFieldLoadsSeparatedByInvokeCustomTestClassGenerator.dump())
        .addKeepAllClassesRule()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), "InstanceFieldLoadsSeparatedByInvokeCustomTestClass")
        .assertSuccess();
  }

  /**
   * Generates a class with the following test method.
   *
   * <pre>
   * TestClass instance = new TestClass();
   * instance.field = Double.MIN_VALUE;
   * setInstanceField(instance, Math.PI); // <- using an invoke-custom instruction
   * assertEquals(Math.PI, instance.field);
   * instance.field = Math.E;
   * assertEquals(Math.E, getInstanceField(instance)); // <- using an invoke-custom instruction
   * </pre>
   */
  static class InstanceFieldLoadsSeparatedByInvokeCustomTestClassGenerator implements Opcodes {

    public static byte[] dump() {
      ClassWriter classWriter = new ClassWriter(0);
      FieldVisitor fieldVisitor;
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_SUPER,
          "InstanceFieldLoadsSeparatedByInvokeCustomTestClass",
          null,
          "java/lang/Object",
          null);

      classWriter.visitSource("InstanceFieldLoadsSeparatedByInvokeCustomTestClass.java", null);

      {
        fieldVisitor = classWriter.visitField(ACC_PRIVATE, "field", "D", null, null);
        fieldVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "InstanceFieldLoadsSeparatedByInvokeCustomTestClass",
            "testInstanceFieldLoadsSeparatedByInvokeCustom",
            "()V",
            false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PRIVATE | ACC_STATIC,
                "testInstanceFieldLoadsSeparatedByInvokeCustom",
                "()V",
                null,
                null);
        methodVisitor.visitCode();
        methodVisitor.visitTypeInsn(NEW, "InstanceFieldLoadsSeparatedByInvokeCustomTestClass");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "InstanceFieldLoadsSeparatedByInvokeCustomTestClass",
            "<init>",
            "()V",
            false);
        methodVisitor.visitVarInsn(ASTORE, 0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitLdcInsn(new Double("4.9E-324"));
        methodVisitor.visitFieldInsn(
            PUTFIELD, "InstanceFieldLoadsSeparatedByInvokeCustomTestClass", "field", "D");
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitLdcInsn(new Double("3.141592653589793"));
        methodVisitor.visitInvokeDynamicInsn(
            "field",
            "(LInstanceFieldLoadsSeparatedByInvokeCustomTestClass;D)V",
            new Handle(
                Opcodes.H_INVOKESTATIC,
                "InstanceFieldLoadsSeparatedByInvokeCustomTestClass",
                "lookupInstanceFieldSetter",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                false),
            new Object[] {});
        methodVisitor.visitLdcInsn(new Double("3.141592653589793"));
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD, "InstanceFieldLoadsSeparatedByInvokeCustomTestClass", "field", "D");
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "InstanceFieldLoadsSeparatedByInvokeCustomTestClass",
            "assertEquals",
            "(DD)V",
            false);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitLdcInsn(new Double("2.718281828459045"));
        methodVisitor.visitFieldInsn(
            PUTFIELD, "InstanceFieldLoadsSeparatedByInvokeCustomTestClass", "field", "D");
        methodVisitor.visitLdcInsn(new Double("2.718281828459045"));
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInvokeDynamicInsn(
            "field",
            "(LInstanceFieldLoadsSeparatedByInvokeCustomTestClass;)D",
            new Handle(
                Opcodes.H_INVOKESTATIC,
                "InstanceFieldLoadsSeparatedByInvokeCustomTestClass",
                "lookupInstanceFieldGetter",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                false),
            new Object[] {});
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "InstanceFieldLoadsSeparatedByInvokeCustomTestClass",
            "assertEquals",
            "(DD)V",
            false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(4, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_STATIC,
                "lookupInstanceFieldSetter",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                null,
                new String[] {"java/lang/Throwable"});
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/invoke/MethodType",
            "parameterType",
            "(I)Ljava/lang/Class;",
            false);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/invoke/MethodType",
            "parameterType",
            "(I)Ljava/lang/Class;",
            false);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/invoke/MethodHandles$Lookup",
            "findSetter",
            "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
            false);
        methodVisitor.visitVarInsn(ASTORE, 3);
        methodVisitor.visitTypeInsn(NEW, "java/lang/invoke/ConstantCallSite");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "java/lang/invoke/ConstantCallSite",
            "<init>",
            "(Ljava/lang/invoke/MethodHandle;)V",
            false);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitMaxs(5, 4);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_STATIC,
                "lookupInstanceFieldGetter",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                null,
                new String[] {"java/lang/Throwable"});
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/invoke/MethodType",
            "parameterType",
            "(I)Ljava/lang/Class;",
            false);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/invoke/MethodType",
            "returnType",
            "()Ljava/lang/Class;",
            false);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/invoke/MethodHandles$Lookup",
            "findGetter",
            "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
            false);
        methodVisitor.visitVarInsn(ASTORE, 3);
        methodVisitor.visitTypeInsn(NEW, "java/lang/invoke/ConstantCallSite");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "java/lang/invoke/ConstantCallSite",
            "<init>",
            "(Ljava/lang/invoke/MethodHandle;)V",
            false);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitMaxs(4, 4);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_STATIC, "assertEquals", "(DD)V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(DLOAD, 0);
        methodVisitor.visitVarInsn(DLOAD, 2);
        methodVisitor.visitInsn(DCMPL);
        Label label1 = new Label();
        methodVisitor.visitJumpInsn(IFNE, label1);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitTypeInsn(NEW, "java/lang/AssertionError");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        methodVisitor.visitLdcInsn("assertEquals d1: ");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            false);
        methodVisitor.visitVarInsn(DLOAD, 0);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "append",
            "(D)Ljava/lang/StringBuilder;",
            false);
        methodVisitor.visitLdcInsn(", d2: ");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            false);
        methodVisitor.visitVarInsn(DLOAD, 2);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/StringBuilder",
            "append",
            "(D)Ljava/lang/StringBuilder;",
            false);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL, "java/lang/AssertionError", "<init>", "(Ljava/lang/Object;)V", false);
        methodVisitor.visitInsn(ATHROW);
        methodVisitor.visitMaxs(5, 4);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
