// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.DescriptorUtils;
import java.nio.file.Path;
import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Jacoco does invalid instrumentation when R8 clobber locals of arguments: TODO(b/117589870) */
public class JacocoRegressionTest extends TestBase implements Opcodes {

  @Test
  public void test() throws Exception {
    Path output = temp.newFolder().toPath();
    String name = "Test";
    String desc = DescriptorUtils.javaTypeToDescriptor(name);
    byte[] bytes = dump();
    Path path = output.resolve("out.jar");
    ArchiveConsumer archiveConsumer = new ArchiveConsumer(path);
    archiveConsumer.accept(ByteDataView.of(bytes), desc, null);
    archiveConsumer.finished(null);

    String expected = "15" + System.lineSeparator();
    ProcessResult result = ToolHelper.runJava(path, name);
    assertEquals(expected, result.stdout);

    Path agentOutput = output.resolve("agent.out");
    ProcessResult result1 =
        ToolHelper.runJava(
            path,
            String.format(
                "-javaagent:%s=destfile=%s,dumponexit=true,output=file",
                ToolHelper.JACOCO_AGENT, agentOutput),
            name);
    assertEquals(1, result1.exitCode);
    assertTrue(result1.toString().contains("java.lang.VerifyError: Bad local variable type"));
  }

  public static byte[] dump() throws Exception {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_8, ACC_FINAL | ACC_SUPER | ACC_PUBLIC, "Test", null, "java/lang/Object", null);
    classWriter.visitSource("Test.java", null);

    {
      methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(32, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable("this", "LTest;", null, label0, label1, 0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }

    // This is the problematic part for Jacoco, where we clobber local 1 with long_2nd
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "foo", "(I)I", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ILOAD, 0);
      methodVisitor.visitInsn(I2L);
      methodVisitor.visitVarInsn(LSTORE, 0);
      methodVisitor.visitIntInsn(BIPUSH, 15);
      methodVisitor.visitInsn(IRETURN);
      methodVisitor.visitMaxs(4, 4);
      methodVisitor.visitEnd();
    }

    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(6, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitIntInsn(BIPUSH, 42);
      methodVisitor.visitMethodInsn(INVOKESTATIC, "Test", "foo", "(I)I", false);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(7, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }

    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
