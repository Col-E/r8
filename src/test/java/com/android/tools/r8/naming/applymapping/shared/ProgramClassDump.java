// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping.shared;

import com.android.tools.r8.naming.applymapping.shared.ProgramWithLibraryClasses.LibraryClass;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// asmified the following:
// class ProgramClass extends LibraryClass {
//   static String PRG_MSG = "ProgramClass::bar";
//   void bar() {
//     System.out.println(PRG_MSG);
//   }
//   public static void main(String[] args) {
//     new AnotherLibraryClass().foo();
//     ProgramClass instance = new ProgramClass();
//     instance.foo();
//     instance.bar();
//   }
// }
//
// then replaced the use of LibraryClass and AnotherLibraryClass with A and B so that providing
// LibraryClass and AnotherLibraryClass, along with mapping, as a minified library:
//   A -> LibraryClass:
//     void a() -> foo
//   B -> AnotherLibraryClass:
//     void a() -> foo
public class ProgramClassDump implements Opcodes {

  public static byte[] dump() {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;

    String pkg = LibraryClass.class.getPackage().getName();

    classWriter.visit(V1_8, ACC_SUPER, pkg.replace('.', '/') + "/ProgramClass", null, "A", null);

    classWriter.visitSource("Test.java", null);

    {
      fieldVisitor = classWriter.visitField(
          ACC_STATIC, "PRG_MSG", "Ljava/lang/String;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(15, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "A", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "bar", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(18, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitFieldInsn(GETSTATIC, "ProgramClass", "PRG_MSG", "Ljava/lang/String;");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(19, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(
          ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(22, label0);
      methodVisitor.visitTypeInsn(NEW, "B");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "B", "<init>", "()V", false);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "B", "a", "()V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(23, label1);
      methodVisitor.visitTypeInsn(NEW, "ProgramClass");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "ProgramClass", "<init>", "()V", false);
      methodVisitor.visitVarInsn(ASTORE, 1);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(24, label2);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "ProgramClass", "a", "()V", false);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(25, label3);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "ProgramClass", "bar", "()V", false);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(26, label4);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(16, label0);
      methodVisitor.visitLdcInsn("ProgramClass::bar");
      methodVisitor.visitFieldInsn(PUTSTATIC, "ProgramClass", "PRG_MSG", "Ljava/lang/String;");
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
