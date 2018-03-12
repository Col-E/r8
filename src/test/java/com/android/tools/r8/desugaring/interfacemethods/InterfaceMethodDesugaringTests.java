// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.desugaring.interfacemethods.test0.InterfaceWithDefaults;
import com.android.tools.r8.desugaring.interfacemethods.test0.TestMain;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(VmTestRunner.class)
public class InterfaceMethodDesugaringTests extends AsmTestBase {

  @Test
  public void testInvokeSpecialToDefaultMethod() throws Exception {
    ensureSameOutput(TestMain.class.getCanonicalName(),
        ToolHelper.getMinApiLevelForDexVm(),
        ToolHelper.getClassAsBytes(TestMain.class),
        introduceInvokeSpecial(ToolHelper.getClassAsBytes(InterfaceWithDefaults.class)));
  }

  private byte[] introduceInvokeSpecial(byte[] classBytes) throws IOException {
    try (InputStream input = new ByteArrayInputStream(classBytes)) {
      ClassReader cr = new ClassReader(input);
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
      cr.accept(
          new ClassVisitor(Opcodes.ASM6, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name,
                String desc, String signature, String[] exceptions) {
              MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
              return new MethodVisitor(Opcodes.ASM6, visitor) {
                @Override
                public void visitMethodInsn(
                    int opcode, String owner, String name, String desc, boolean itf) {
                  if (opcode == Opcodes.INVOKEINTERFACE &&
                      owner.endsWith("test0/InterfaceWithDefaults") &&
                      name.equals("foo")) {
                    super.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, name, desc, itf);

                  } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                  }
                }
              };
            }
          }, 0);
      return cw.toByteArray();
    }
  }
}
