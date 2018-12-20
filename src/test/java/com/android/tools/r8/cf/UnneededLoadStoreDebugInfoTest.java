// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import java.nio.file.Path;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class UnneededLoadStoreDebugInfoTest extends TestBase {

  private static final String CLASS_NAME = "UnneededLoadStoreDebugInfoTest";
  private static final String DESCRIPTOR = "L" + CLASS_NAME + ";";

  public static class Dump implements Opcodes {

    public static byte[] dump() {
      ClassWriter cw = new ClassWriter(0);
      MethodVisitor mv;

      cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, CLASS_NAME, null, "java/lang/Object", null);

      {
        mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        mv.visitCode();
        Label argsStart = new Label();
        mv.visitLabel(argsStart);
        mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        Label argsEnd = new Label();
        mv.visitLabel(argsEnd);
        mv.visitTypeInsn(CHECKCAST, "java/io/PrintStream");
        mv.visitInsn(POP);
        mv.visitInsn(RETURN);
        mv.visitLocalVariable("args", "[Ljava/lang/String;", null, argsStart, argsEnd, 0);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
      }
      cw.visitEnd();

      return cw.toByteArray();
    }
  }

  @Test
  public void test() throws Exception {
    Path inputJar = temp.getRoot().toPath().resolve("input.jar");
    ArchiveConsumer consumer = new ArchiveConsumer(inputJar);
    consumer.accept(ByteDataView.of(Dump.dump()), DESCRIPTOR, null);
    consumer.finished(null);
    ProcessResult runInput = ToolHelper.runJava(inputJar, CLASS_NAME);
    assertEquals(0, runInput.exitCode);
    Path outputJar = temp.getRoot().toPath().resolve("output.jar");
    R8.run(
        R8Command.builder()
            .setMode(CompilationMode.DEBUG)
            .setDisableTreeShaking(true)
            .setDisableMinification(true)
            .addProgramFiles(inputJar)
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setProgramConsumer(new ArchiveConsumer(outputJar))
            .build());
    ProcessResult runOutput = ToolHelper.runJava(outputJar, CLASS_NAME);
    assertEquals(runInput.toString(), runOutput.toString());
  }
}
