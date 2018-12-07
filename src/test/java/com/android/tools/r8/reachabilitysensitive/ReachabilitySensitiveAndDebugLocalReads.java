// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.reachabilitysensitive;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ReachabilitySensitiveAndDebugLocalReads extends TestBase {

  @Test
  public void test() throws Exception {
    byte[] classdata = Dump.dump();
    String mainClass = "test.Test";
    String expected = StringUtils.lines("5");
    testForJvm()
        .addProgramClassFileData(classdata)
        .run(mainClass)
        .assertSuccessWithOutput(expected);

    testForD8()
        .release()
        .addProgramClassFileData(classdata)
        .run(mainClass)
        .assertSuccessWithOutput(expected);
  }
}

/* ASM Dump of ReachabilitySensitive/TestClassWithAnnotatedMethod + main method:
<pre>
 package test;
 public class Test {

  @ReachabilitySensitive
  public void unrelatedAnnotatedMethod() {}

  public void method() {
    int i = 2;
    int j = i + 1;
    int k = j + 2;
    System.out.println(k);
  }

  public static void main(String[] args) {
    new Test().method();
  }
}
</pre>
 */
class Dump implements Opcodes {

  public static byte[] dump() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, "test/Test", null, "java/lang/Object", null);

    classWriter.visitSource("Test.java", null);

    {
      methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(53, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable("this", "Ltest/Test;", null, label0, label1, 0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }

    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC, "unrelatedAnnotatedMethod", "()V", null, null);
      {
        annotationVisitor0 =
            methodVisitor.visitAnnotation(
                "Ldalvik/annotation/optimization/ReachabilitySensitive;", true);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(56, label0);
      methodVisitor.visitInsn(RETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable("this", "Ltest/Test;", null, label0, label1, 0);
      methodVisitor.visitMaxs(0, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "method", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(59, label0);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitVarInsn(ISTORE, 1);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(60, label1);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitInsn(IADD);
      methodVisitor.visitVarInsn(ISTORE, 2);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(61, label2);
      methodVisitor.visitVarInsn(ILOAD, 2);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitInsn(IADD);
      methodVisitor.visitVarInsn(ISTORE, 3);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(62, label3);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(ILOAD, 3);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(63, label4);
      // Insert some unneeded code which we know will be removed.
      methodVisitor.visitLdcInsn(1);
      methodVisitor.visitLdcInsn(2);
      methodVisitor.visitInsn(IADD);
      // Insert an label that will end some local variables so removing will create local reads.
      Label labelForEndingLocals = new Label();
      methodVisitor.visitLabel(labelForEndingLocals);
      methodVisitor.visitInsn(POP);
      // Pop the unneeded value and continue as usual.
      methodVisitor.visitInsn(RETURN);
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLocalVariable("this", "Ltest/Test;", null, label0, label5, 0);
      methodVisitor.visitLocalVariable("i", "I", null, label1, labelForEndingLocals, 1);
      methodVisitor.visitLocalVariable("j", "I", null, label2, labelForEndingLocals, 2);
      methodVisitor.visitLocalVariable("k", "I", null, label3, labelForEndingLocals, 3);
      methodVisitor.visitMaxs(2, 4);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(66, label0);
      methodVisitor.visitTypeInsn(NEW, "test/Test");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "test/Test", "<init>", "()V", false);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "test/Test", "method", "()V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(67, label1);
      methodVisitor.visitInsn(RETURN);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLocalVariable("args", "[Ljava/lang/String;", null, label0, label2, 0);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
