// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.VmTestRunner.IgnoreForRangeOfVmVersions;
import com.android.tools.r8.desugaring.interfacemethods.default0.TestMainDefault0;
import com.android.tools.r8.desugaring.interfacemethods.default1.Derived1;
import com.android.tools.r8.desugaring.interfacemethods.default1.DerivedComparator1;
import com.android.tools.r8.desugaring.interfacemethods.default1.TestMainDefault1;
import com.android.tools.r8.desugaring.interfacemethods.default2.Derived2;
import com.android.tools.r8.desugaring.interfacemethods.default2.DerivedComparator2;
import com.android.tools.r8.desugaring.interfacemethods.default2.TestMainDefault2;
import com.android.tools.r8.desugaring.interfacemethods.static0.TestMainStatic0;
import com.android.tools.r8.desugaring.interfacemethods.static1.TestMainStatic1;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(VmTestRunner.class)
public class InterfaceMethodDesugaringTests extends AsmTestBase {

  private static List<String> getArgs(int startWith) {
    return Collections.singletonList(
        String.valueOf(ToolHelper.getMinApiLevelForDexVm().getLevel() >= startWith));
  }

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

  @Test
  public void testInvokeStatic0a() throws Exception {
    ensureSameOutput(
        TestMainStatic0.class.getCanonicalName(),
        AndroidApiLevel.K,
        getArgs(AndroidApiLevel.N.getLevel()),
        ToolHelper.getClassAsBytes(TestMainStatic0.class));
  }

  @Test
  public void testInvokeStatic0b() throws Exception {
    ensureSameOutput(
        TestMainStatic0.class.getCanonicalName(),
        ToolHelper.getMinApiLevelForDexVm(),
        getArgs(AndroidApiLevel.N.getLevel()),
        ToolHelper.getClassAsBytes(TestMainStatic0.class));
  }

  @Test
  public void testInvokeStatic1a() throws Exception {
    ensureSameOutput(
        TestMainStatic1.class.getCanonicalName(),
        AndroidApiLevel.K,
        getArgs(AndroidApiLevel.N.getLevel()),
        ToolHelper.getClassAsBytes(TestMainStatic1.class));
  }

  @Test
  public void testInvokeStatic1b() throws Exception {
    ensureSameOutput(
        TestMainStatic1.class.getCanonicalName(),
        ToolHelper.getMinApiLevelForDexVm(),
        getArgs(AndroidApiLevel.N.getLevel()),
        ToolHelper.getClassAsBytes(TestMainStatic1.class));
  }

  @Test
  public void testInvokeDefault0a() throws Exception {
    ensureSameOutput(
        TestMainDefault0.class.getCanonicalName(),
        AndroidApiLevel.K,
        getArgs(AndroidApiLevel.N.getLevel()),
        ToolHelper.getClassAsBytes(TestMainDefault0.class));
  }

  @Test
  public void testInvokeDefault0b() throws Exception {
    ensureSameOutput(
        TestMainDefault0.class.getCanonicalName(),
        ToolHelper.getMinApiLevelForDexVm(),
        getArgs(AndroidApiLevel.N.getLevel()),
        ToolHelper.getClassAsBytes(TestMainDefault0.class));
  }

  @Test(expected = CompilationFailedException.class)
  @IgnoreForRangeOfVmVersions(from = Version.V7_0_0, to = Version.DEFAULT) // No desugaring
  public void testInvokeDefault1() throws Exception {
    ensureSameOutput(
        TestMainDefault1.class.getCanonicalName(),
        ToolHelper.getMinApiLevelForDexVm(),
        getArgs(AndroidApiLevel.N.getLevel()),
        ToolHelper.getClassAsBytes(TestMainDefault1.class),
        ToolHelper.getClassAsBytes(Derived1.class),
        ToolHelper.getClassAsBytes(DerivedComparator1.class));
  }

  @Test()
  public void testInvokeDefault2a() throws Exception {
    ensureSameOutput(
        TestMainDefault2.class.getCanonicalName(),
        AndroidApiLevel.K,
        getArgs(AndroidApiLevel.N.getLevel()),
        ToolHelper.getClassAsBytes(TestMainDefault2.class),
        ToolHelper.getClassAsBytes(Derived2.class),
        ToolHelper.getClassAsBytes(DerivedComparator2.class));
  }

  @Test()
  public void testInvokeDefault2b() throws Exception {
    ensureSameOutput(
        TestMainDefault2.class.getCanonicalName(),
        ToolHelper.getMinApiLevelForDexVm(),
        getArgs(AndroidApiLevel.N.getLevel()),
        ToolHelper.getClassAsBytes(TestMainDefault2.class),
        ToolHelper.getClassAsBytes(Derived2.class),
        ToolHelper.getClassAsBytes(DerivedComparator2.class));
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
