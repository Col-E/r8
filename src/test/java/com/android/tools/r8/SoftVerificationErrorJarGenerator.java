// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ZipUtils;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class SoftVerificationErrorJarGenerator {

  public enum ApiCallerName {
    CONSTRUCT_UNKNOWN("constructUnknownObject"),
    CALL_UNKNOWN("callUnknownMethod"),
    CONSTRUCT_AND_CALL_UNKNOWN("constructUnknownObjectAndCallUnknownMethod");

    private String apiCallerMethodName;

    ApiCallerName(String apiCallerMethodName) {
      this.apiCallerMethodName = apiCallerMethodName;
    }

    public String getApiCallerMethodName() {
      return apiCallerMethodName;
    }
  }

  public static String NEW_API_CLASS_NAME = "android/app/NotificationChannel";
  public static String NEW_API_CLASS_METHOD_NAME = "setDescription";
  public static String EXISTING_API_METHOD_NAME = "createNotificationChannel";

  public static void createJar(
      Path archive,
      int numberOfClasses,
      boolean isOutlined,
      ApiCallerName callerName,
      String newApiClassName,
      String newApiClassMethodName,
      String existingApiNewMethodName)
      throws IOException {
    OpenOption[] options =
        new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
    try (ZipOutputStream out =
        new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(archive, options)))) {
      IntBox intBox = new IntBox(0);
      Pair<byte[], String> callerPair =
          isOutlined
              ? Dumps.dumpApiCallerInlined(
                  -1,
                  callerName.getApiCallerMethodName(),
                  newApiClassName,
                  newApiClassMethodName,
                  existingApiNewMethodName)
              : null;
      if (callerPair != null) {
        // The outlined code will call the apiCallerName on the callerPair code.
        ZipUtils.writeToZipStream(
            out, callerPair.getSecond() + ".class", callerPair.getFirst(), ZipEntry.STORED);
      }
      byte[] mainBytes =
          Dumps.dumpMain(
              () -> {
                if (intBox.get() > numberOfClasses) {
                  return null;
                }
                Pair<byte[], String> classData =
                    isOutlined
                        ? Dumps.dumpApiCallerOutlined(
                            intBox.getAndIncrement(),
                            callerPair.getSecond(),
                            callerName.getApiCallerMethodName())
                        : Dumps.dumpApiCallerInlined(
                            intBox.getAndIncrement(),
                            callerName.getApiCallerMethodName(),
                            newApiClassName,
                            newApiClassMethodName,
                            existingApiNewMethodName);
                try {
                  ZipUtils.writeToZipStream(
                      out, classData.getSecond() + ".class", classData.getFirst(), ZipEntry.STORED);
                } catch (IOException exception) {
                  throw new RuntimeException(exception);
                }
                return classData.getSecond();
              });
      ZipUtils.writeToZipStream(
          out, "com/example/softverificationsample/TestRunner.class", mainBytes, ZipEntry.STORED);
    }
  }

  public static class Dumps implements Opcodes {

    public static byte[] dumpMain(Supplier<String> targetSupplier) {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_PUBLIC | ACC_SUPER,
          "com/example/softverificationsample/TestRunner",
          null,
          "java/lang/Object",
          null);

      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC, "run", "(Landroid/content/Context;)V", null, null);
        methodVisitor.visitCode();
        String target = targetSupplier.get();
        while (target != null) {
          methodVisitor.visitVarInsn(ALOAD, 0);
          methodVisitor.visitMethodInsn(
              INVOKESTATIC, target, "callApi", "(Landroid/content/Context;)V", false);
          target = targetSupplier.get();
        }
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }

    public static Pair<byte[], String> dumpApiCallerOutlined(
        int index, String apiCallerName, String apiMethodCaller) {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      String binaryName =
          "com/example/softverificationsample/ApiCallerOutlined" + (index > -1 ? index : "");

      classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, binaryName, null, "java/lang/Object", null);

      classWriter.visitInnerClass(
          "android/os/Build$VERSION", "android/os/Build", "VERSION", ACC_PUBLIC | ACC_STATIC);

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
                ACC_PUBLIC | ACC_STATIC, "callApi", "(Landroid/content/Context;)V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitFieldInsn(GETSTATIC, "android/os/Build$VERSION", "SDK_INT", "I");
        methodVisitor.visitIntInsn(BIPUSH, 26);
        Label label0 = new Label();
        methodVisitor.visitJumpInsn(IF_ICMPLT, label0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC, apiCallerName, apiMethodCaller, "(Landroid/content/Context;)V", false);
        methodVisitor.visitLabel(label0);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return Pair.create(classWriter.toByteArray(), binaryName);
    }

    public static Pair<byte[], String> dumpApiCallerInlined(
        int index,
        String apiMethodCaller,
        String newApiClassName,
        String newApiClassMethodName,
        String existingApiNewMethodName) {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      String binaryName =
          "com/example/softverificationsample/ApiCallerInlined" + (index > -1 ? index : "");

      classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, binaryName, null, "java/lang/Object", null);

      classWriter.visitInnerClass(
          "android/os/Build$VERSION", "android/os/Build", "VERSION", ACC_PUBLIC | ACC_STATIC);

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
                ACC_PUBLIC | ACC_STATIC, "callApi", "(Landroid/content/Context;)V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitFieldInsn(GETSTATIC, "android/os/Build$VERSION", "SDK_INT", "I");
        methodVisitor.visitIntInsn(BIPUSH, 26);
        Label label0 = new Label();
        methodVisitor.visitJumpInsn(IF_ICMPLT, label0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC, binaryName, apiMethodCaller, "(Landroid/content/Context;)V", false);
        methodVisitor.visitLabel(label0);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC,
                "constructUnknownObject",
                "(Landroid/content/Context;)V",
                null,
                null);
        methodVisitor.visitCode();
        methodVisitor.visitTypeInsn(NEW, newApiClassName);
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitLdcInsn("CHANNEL_ID");
        methodVisitor.visitLdcInsn("FOO");
        methodVisitor.visitFieldInsn(
            GETSTATIC, "android/app/NotificationManager", "IMPORTANCE_DEFAULT", "I");
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            newApiClassName,
            "<init>",
            "(Ljava/lang/String;Ljava/lang/String;I)V",
            false);
        methodVisitor.visitVarInsn(ASTORE, 1);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitLdcInsn("This is a test channel");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, newApiClassName, newApiClassMethodName, "(Ljava/lang/String;)V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(5, 2);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC,
                "callUnknownMethod",
                "(Landroid/content/Context;)V",
                null,
                null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitLdcInsn(Type.getType("Landroid/app/NotificationManager;"));
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "android/content/Context",
            "getSystemService",
            "(Ljava/lang/Class;)Ljava/lang/Object;",
            false);
        methodVisitor.visitTypeInsn(CHECKCAST, "android/app/NotificationManager");
        methodVisitor.visitVarInsn(ASTORE, 1);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "android/app/NotificationManager",
            existingApiNewMethodName,
            "(L" + newApiClassName + ";)V",
            false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(2, 2);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC,
                "constructUnknownObjectAndCallUnknownMethod",
                "(Landroid/content/Context;)V",
                null,
                null);
        methodVisitor.visitCode();
        methodVisitor.visitTypeInsn(NEW, newApiClassName);
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitLdcInsn("CHANNEL_ID");
        methodVisitor.visitLdcInsn("FOO");
        methodVisitor.visitFieldInsn(
            GETSTATIC, "android/app/NotificationManager", "IMPORTANCE_DEFAULT", "I");
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            newApiClassName,
            "<init>",
            "(Ljava/lang/String;Ljava/lang/String;I)V",
            false);
        methodVisitor.visitVarInsn(ASTORE, 1);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitLdcInsn("This is a test channel");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, newApiClassName, newApiClassMethodName, "(Ljava/lang/String;)V", false);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitLdcInsn(Type.getType("Landroid/app/NotificationManager;"));
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "android/content/Context",
            "getSystemService",
            "(Ljava/lang/Class;)Ljava/lang/Object;",
            false);
        methodVisitor.visitTypeInsn(CHECKCAST, "android/app/NotificationManager");
        methodVisitor.visitVarInsn(ASTORE, 2);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "android/app/NotificationManager",
            existingApiNewMethodName,
            "(L" + newApiClassName + ";)V",
            false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(5, 3);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return Pair.create(classWriter.toByteArray(), binaryName);
    }
  }
}
