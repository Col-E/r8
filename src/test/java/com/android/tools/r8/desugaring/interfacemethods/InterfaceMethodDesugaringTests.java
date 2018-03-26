// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.VmTestRunner.IgnoreForRangeOfVmVersions;
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
    ensureSameOutput(
        com.android.tools.r8.desugaring.interfacemethods.test0.TestMain.class.getCanonicalName(),
        ToolHelper.getMinApiLevelForDexVm(),
        ToolHelper.getClassAsBytes(
            com.android.tools.r8.desugaring.interfacemethods.test0.TestMain.class),
        patchInterfaceWithDefaults(ToolHelper.getClassAsBytes(
            com.android.tools.r8.desugaring.interfacemethods.test0.InterfaceWithDefaults.class)));
  }

  // NOTE: this particular test is working on pre-N devices since
  //       it's fixed by interface default method desugaring.
  @IgnoreForRangeOfVmVersions(from = Version.V7_0_0, to = Version.DEFAULT)
  @Test
  public void testInvokeSpecialToDefaultMethodFromStatic() throws Exception {
    ensureSameOutput(
        com.android.tools.r8.desugaring.interfacemethods.test1.TestMain.class.getCanonicalName(),
        ToolHelper.getMinApiLevelForDexVm(),
        ToolHelper.getClassAsBytes(
            com.android.tools.r8.desugaring.interfacemethods.test1.TestMain.class),
        patchInterfaceWithDefaults(ToolHelper.getClassAsBytes(
            com.android.tools.r8.desugaring.interfacemethods.test1.InterfaceWithDefaults.class)));
  }

  @Test
  public void testInvokeSpecialToInheritedDefaultMethod() throws Exception {
    ensureSameOutput(
        com.android.tools.r8.desugaring.interfacemethods.test2.TestMain.class.getCanonicalName(),
        ToolHelper.getMinApiLevelForDexVm(),
        ToolHelper.getClassAsBytes(
            com.android.tools.r8.desugaring.interfacemethods.test2.TestMain.class),
        ToolHelper.getClassAsBytes(
            com.android.tools.r8.desugaring.interfacemethods.test2.Test.class),
        ToolHelper.getClassAsBytes(
            com.android.tools.r8.desugaring.interfacemethods.test2.LeftTest.class),
        ToolHelper.getClassAsBytes(
            com.android.tools.r8.desugaring.interfacemethods.test2.RightTest.class),
        ToolHelper.getClassAsBytes(
            com.android.tools.r8.desugaring.interfacemethods.test2.Test2.class));
  }

  private static class MutableInteger {
    int value;
  }

  private byte[] patchInterfaceWithDefaults(byte[] classBytes) throws IOException {
    MutableInteger patched = new MutableInteger();
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
                      owner.endsWith("InterfaceWithDefaults") &&
                      name.equals("foo")) {
                    assertEquals(0, patched.value);
                    super.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, name, desc, itf);
                    patched.value++;

                  } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                  }
                }
              };
            }
          }, 0);
      assertEquals(1, patched.value);
      return cw.toByteArray();
    }
  }
}
