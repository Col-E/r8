// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class B148366506 extends TestBase implements Opcodes {

  private final TestParameters parameters;
  private final CompilationMode compilationMode;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(), CompilationMode.values());
  }

  public B148366506(TestParameters parameters, CompilationMode compilationMode) {
    this.parameters = parameters;
    this.compilationMode = compilationMode;
  }

  @Test
  public void test() throws Exception {
    testForD8()
        .addProgramClassFileData(dump())
        .setMinApi(parameters)
        .setMode(compilationMode)
        .compile();
  }

  // Code from b/148366506.
  public static byte[] dump() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(
        V1_6,
        ACC_PUBLIC | ACC_SUPER,
        "d/b/c/e/e/a/b/a",
        null,
        "java/lang/Object",
        new String[] {"com/microsoft/identity/common/adal/internal/cache/IStorageHelper"});

    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_SYNCHRONIZED, "e", "()Ljavax/crypto/SecretKey;", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      Label label1 = new Label();
      Label label2 = new Label();
      methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Exception");
      Label label3 = new Label();
      Label label4 = new Label();
      Label label5 = new Label();
      methodVisitor.visitTryCatchBlock(label3, label4, label5, null);
      Label label6 = new Label();
      methodVisitor.visitTryCatchBlock(label6, label5, label2, "java/lang/Exception");
      Label label7 = new Label();
      Label label8 = new Label();
      Label label9 = new Label();
      methodVisitor.visitTryCatchBlock(label7, label8, label9, null);
      Label label10 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label10);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitFrame(
          Opcodes.F_FULL, 0, new Object[] {}, 1, new Object[] {"java/lang/Exception"});
      methodVisitor.visitInsn(ATHROW);
      Label label11 = new Label();
      methodVisitor.visitLabel(label11);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"d/b/c/e/e/a/b/a"});
      methodVisitor.visitInsn(ICONST_0);
      Label label12 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label12);
      Label label13 = new Label();
      methodVisitor.visitLabel(label13);
      methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"d/b/c/e/e/a/b/a"}, 0, null);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitLabel(label0);
      methodVisitor.visitFieldInsn(
          GETFIELD, "d/b/c/e/e/a/b/a", "mContext", "Landroid/content/Context;");
      methodVisitor.visitLabel(label1);
      Label label14 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label14);
      Label label15 = new Label();
      methodVisitor.visitLabel(label15);
      methodVisitor.visitFrame(
          Opcodes.F_SAME1, 0, null, 1, new Object[] {"android/content/Context"});
      methodVisitor.visitFieldInsn(GETSTATIC, "d/b/c/e/e/a/b/a", "\u0131", "I");
      methodVisitor.visitIntInsn(BIPUSH, 111);
      methodVisitor.visitInsn(IADD);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitIntInsn(SIPUSH, 128);
      methodVisitor.visitInsn(IREM);
      methodVisitor.visitFieldInsn(PUTSTATIC, "d/b/c/e/e/a/b/a", "\u03b9", "I");
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitInsn(IREM);
      Label label16 = new Label();
      methodVisitor.visitJumpInsn(IFNE, label16);
      Label label17 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label17);
      methodVisitor.visitLabel(label16);
      methodVisitor.visitFrame(
          Opcodes.F_SAME1, 0, null, 1, new Object[] {"android/content/Context"});
      methodVisitor.visitJumpInsn(GOTO, label3);
      Label label18 = new Label();
      methodVisitor.visitLabel(label18);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          0,
          new Object[] {},
          2,
          new Object[] {"d/b/c/e/e/a/b/a", "java/lang/String"});
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitInsn(IREM);
      methodVisitor.visitInsn(POP);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          1,
          new Object[] {"d/b/c/e/e/a/b/a"},
          1,
          new Object[] {"android/content/Context"});
      methodVisitor.visitLdcInsn("android.content.Context");
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "java/lang/Class",
          "forName",
          "(Ljava/lang/String;)Ljava/lang/Class;",
          false);
      methodVisitor.visitLdcInsn("getPackageName");
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/Class",
          "getMethod",
          "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;",
          false);
      methodVisitor.visitInsn(SWAP);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/reflect/Method",
          "invoke",
          "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
          false);
      methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
      methodVisitor.visitLabel(label4);
      methodVisitor.visitVarInsn(ASTORE, 1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "d/b/c/e/e/a/b/a", "getSecretKeyData", "(Ljava/lang/String;)[B", false);
      methodVisitor.visitVarInsn(ASTORE, 2);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 2);
      Label label19 = new Label();
      methodVisitor.visitJumpInsn(IFNONNULL, label19);
      Label label20 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label20);
      methodVisitor.visitLabel(label19);
      methodVisitor.visitFrame(
          Opcodes.F_FULL, 0, new Object[] {}, 1, new Object[] {"d/b/c/e/e/a/b/a"});
      Label label21 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label21);
      Label label22 = new Label();
      methodVisitor.visitLabel(label22);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"d/b/c/e/e/a/b/a"});
      methodVisitor.visitLdcInsn("A001");
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label12);
      methodVisitor.visitFrame(
          Opcodes.F_FULL, 0, new Object[] {}, 2, new Object[] {"d/b/c/e/e/a/b/a", Opcodes.INTEGER});
      Label label23 = new Label();
      methodVisitor.visitTableSwitchInsn(0, 1, label22, new Label[] {label22, label23});
      Label label24 = new Label();
      methodVisitor.visitLabel(label24);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"d/b/c/e/e/a/b/a"});
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitJumpInsn(GOTO, label12);
      methodVisitor.visitLabel(label9);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label6);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          0,
          new Object[] {},
          2,
          new Object[] {"d/b/c/e/e/a/b/a", "java/lang/String"});
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "d/b/c/e/e/a/b/a",
          "loadSecretKeyForEncryption",
          "(Ljava/lang/String;)Ljavax/crypto/SecretKey;",
          false);
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitLabel(label5);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Throwable", "getCause", "()Ljava/lang/Throwable;", false);
      methodVisitor.visitInsn(DUP);
      Label label25 = new Label();
      methodVisitor.visitJumpInsn(IFNULL, label25);
      Label label26 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label26);
      methodVisitor.visitLabel(label25);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          0,
          new Object[] {},
          2,
          new Object[] {"java/lang/Throwable", "java/lang/Throwable"});
      Label label27 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label27);
      Label label28 = new Label();
      methodVisitor.visitLabel(label28);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          1,
          new Object[] {"d/b/c/e/e/a/b/a"},
          1,
          new Object[] {"android/content/Context"});
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitInsn(IREM);
      methodVisitor.visitInsn(POP);
      methodVisitor.visitJumpInsn(GOTO, label15);
      Label label29 = new Label();
      methodVisitor.visitLabel(label29);
      methodVisitor.visitFrame(
          Opcodes.F_FULL, 0, new Object[] {}, 1, new Object[] {"d/b/c/e/e/a/b/a"});
      methodVisitor.visitLdcInsn("U001");
      methodVisitor.visitJumpInsn(GOTO, label18);
      methodVisitor.visitLabel(label10);
      methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"d/b/c/e/e/a/b/a"}, 0, null);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitInsn(IREM);
      methodVisitor.visitInsn(POP);
      Label label30 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label30);
      methodVisitor.visitLabel(label20);
      methodVisitor.visitFrame(
          Opcodes.F_FULL, 0, new Object[] {}, 1, new Object[] {"d/b/c/e/e/a/b/a"});
      methodVisitor.visitInsn(ICONST_1);
      Label label31 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label31);
      Label label32 = new Label();
      methodVisitor.visitLabel(label32);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          1,
          new Object[] {"d/b/c/e/e/a/b/a"},
          1,
          new Object[] {"android/content/Context"});
      methodVisitor.visitJumpInsn(GOTO, label28);
      methodVisitor.visitLabel(label31);
      methodVisitor.visitFrame(
          Opcodes.F_FULL, 0, new Object[] {}, 2, new Object[] {"d/b/c/e/e/a/b/a", Opcodes.INTEGER});
      Label label33 = new Label();
      methodVisitor.visitTableSwitchInsn(0, 1, label29, new Label[] {label29, label33});
      methodVisitor.visitLabel(label26);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          0,
          new Object[] {},
          2,
          new Object[] {"java/lang/Throwable", "java/lang/Throwable"});
      methodVisitor.visitInsn(SWAP);
      methodVisitor.visitInsn(POP);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label21);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"d/b/c/e/e/a/b/a"});
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitJumpInsn(GOTO, label31);
      methodVisitor.visitLabel(label17);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          1,
          new Object[] {"d/b/c/e/e/a/b/a"},
          1,
          new Object[] {"android/content/Context"});
      methodVisitor.visitJumpInsn(GOTO, label3);
      methodVisitor.visitLabel(label30);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "d/b/c/e/e/a/b/a", "\u0131", "I");
      methodVisitor.visitIntInsn(BIPUSH, 47);
      methodVisitor.visitInsn(IADD);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitIntInsn(SIPUSH, 128);
      methodVisitor.visitInsn(IREM);
      methodVisitor.visitFieldInsn(PUTSTATIC, "d/b/c/e/e/a/b/a", "\u03b9", "I");
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitInsn(IREM);
      Label label34 = new Label();
      methodVisitor.visitJumpInsn(IFNE, label34);
      methodVisitor.visitJumpInsn(GOTO, label8);
      methodVisitor.visitLabel(label34);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitJumpInsn(GOTO, label13);
      methodVisitor.visitLabel(label33);
      methodVisitor.visitFrame(
          Opcodes.F_FULL, 0, new Object[] {}, 1, new Object[] {"d/b/c/e/e/a/b/a"});
      methodVisitor.visitFieldInsn(GETSTATIC, "d/b/c/e/e/a/b/a", "\u03b9", "I");
      methodVisitor.visitIntInsn(BIPUSH, 35);
      methodVisitor.visitInsn(IADD);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitIntInsn(SIPUSH, 128);
      methodVisitor.visitInsn(IREM);
      methodVisitor.visitFieldInsn(PUTSTATIC, "d/b/c/e/e/a/b/a", "\u0131", "I");
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitInsn(IREM);
      Label label35 = new Label();
      methodVisitor.visitJumpInsn(IFEQ, label35);
      methodVisitor.visitJumpInsn(GOTO, label24);
      methodVisitor.visitLabel(label35);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"d/b/c/e/e/a/b/a"});
      methodVisitor.visitJumpInsn(GOTO, label11);
      methodVisitor.visitLabel(label14);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          1,
          new Object[] {"d/b/c/e/e/a/b/a"},
          1,
          new Object[] {"android/content/Context"});
      methodVisitor.visitFieldInsn(GETSTATIC, "d/b/c/e/e/a/b/a", "\u03b9", "I");
      methodVisitor.visitIntInsn(BIPUSH, 45);
      methodVisitor.visitInsn(IADD);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitIntInsn(SIPUSH, 128);
      methodVisitor.visitInsn(IREM);
      methodVisitor.visitFieldInsn(PUTSTATIC, "d/b/c/e/e/a/b/a", "\u0131", "I");
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitInsn(IREM);
      Label label36 = new Label();
      methodVisitor.visitJumpInsn(IFEQ, label36);
      methodVisitor.visitJumpInsn(GOTO, label32);
      methodVisitor.visitLabel(label36);
      methodVisitor.visitFrame(
          Opcodes.F_SAME1, 0, null, 1, new Object[] {"android/content/Context"});
      methodVisitor.visitJumpInsn(GOTO, label28);
      methodVisitor.visitLabel(label27);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          0,
          new Object[] {},
          2,
          new Object[] {"java/lang/Throwable", "java/lang/Throwable"});
      methodVisitor.visitInsn(POP);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label23);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"d/b/c/e/e/a/b/a"});
      methodVisitor.visitLdcInsn("A001");
      methodVisitor.visitLabel(label7);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitInsn(ARRAYLENGTH);
      methodVisitor.visitInsn(POP);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label8);
      methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"d/b/c/e/e/a/b/a"}, 0, null);
      methodVisitor.visitJumpInsn(GOTO, label13);
      methodVisitor.visitMaxs(4, 3);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
