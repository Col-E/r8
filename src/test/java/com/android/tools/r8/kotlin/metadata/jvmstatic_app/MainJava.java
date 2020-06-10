// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.jvmstatic_app;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class MainJava implements Opcodes {

  // The java code cannot reference the kotlin-code when running in gradle, so we have it here
  // as a dump.

  // public static void main(String[] args) {
  //   InterfaceWithCompanion.greet("Hello");
  //   Lib.staticFun(() -> true);
  //   Lib.setStaticProp("Foo");
  //   System.out.println(Lib.getStaticProp());
  // }

  public static byte[] dump() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(
        V1_8,
        ACC_PUBLIC | ACC_SUPER,
        "com/android/tools/r8/kotlin/metadata/jvmstatic_app/MainJava",
        null,
        "java/lang/Object",
        null);

    classWriter.visitInnerClass(
        "java/lang/invoke/MethodHandles$Lookup",
        "java/lang/invoke/MethodHandles",
        "Lookup",
        ACC_PUBLIC | ACC_FINAL | ACC_STATIC);

    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
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
      methodVisitor.visitLdcInsn("Hello");
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/kotlin/metadata/jvmstatic_lib/InterfaceWithCompanion",
          "greet",
          "(Ljava/lang/String;)V",
          true);
      methodVisitor.visitInvokeDynamicInsn(
          "invoke",
          "()Lkotlin/jvm/functions/Function0;",
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "java/lang/invoke/LambdaMetafactory",
              "metafactory",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
              false),
          new Object[] {
            Type.getType("()Ljava/lang/Object;"),
            new Handle(
                Opcodes.H_INVOKESTATIC,
                "com/android/tools/r8/kotlin/metadata/jvmstatic_app/MainJava",
                "lambda$main$0",
                "()Ljava/lang/Boolean;",
                false),
            Type.getType("()Ljava/lang/Boolean;")
          });
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/kotlin/metadata/jvmstatic_lib/Lib",
          "staticFun",
          "(Lkotlin/jvm/functions/Function0;)V",
          false);
      methodVisitor.visitLdcInsn("Foo");
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/kotlin/metadata/jvmstatic_lib/Lib",
          "setStaticProp",
          "(Ljava/lang/String;)V",
          false);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/kotlin/metadata/jvmstatic_lib/Lib",
          "getStaticProp",
          "()Ljava/lang/String;",
          false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
              "lambda$main$0",
              "()Ljava/lang/Boolean;",
              null,
              null);
      methodVisitor.visitCode();
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
