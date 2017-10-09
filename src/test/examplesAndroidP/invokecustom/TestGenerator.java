// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package invokecustom;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class TestGenerator {

  private final Path classNamePath;

  public static void main(String[] args) throws IOException {
    assert args.length == 1;
    TestGenerator testGenerator = new TestGenerator(Paths.get(args[0],
        TestGenerator.class.getPackage().getName(), InvokeCustom.class.getSimpleName() + ".class"));
    testGenerator.generateTests();
  }

  public TestGenerator(Path classNamePath) {
    this.classNamePath = classNamePath;
  }

  private void generateTests() throws IOException {
    ClassReader cr = new ClassReader(new FileInputStream(classNamePath.toFile()));
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cr.accept(
        new ClassVisitor(Opcodes.ASM6, cw) {
          @Override
          public void visitEnd() {
            generateMethodTest1(cw);
            generateMethodMain(cw);
            super.visitEnd();
          }
        }, 0);
    new FileOutputStream(classNamePath.toFile()).write(cw.toByteArray());
  }

  /* Generate main method that only call all test methods. */
  private void generateMethodMain(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(
            Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, Type.getInternalName(InvokeCustom.class), "test1", "()V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   *  Generate test with an invokedynamic, a static bootstrap method without extra args and
   *  args to the target method.
   */
  private void generateMethodTest1(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test1", "()V",
        null, null);
    MethodType mt = MethodType.methodType(
            CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class);
    Handle bootstrap = new Handle( Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmLookupStatic", mt.toMethodDescriptorString(), false);
    mv.visitLdcInsn(new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "targetMethodTest1", "()V", false));
    mv.visitLdcInsn(new Handle(Opcodes.H_GETSTATIC, Type.getInternalName(InvokeCustom.class),
        "staticField1", "Ljava/lang/String;", false));
    mv.visitInvokeDynamicInsn("targetMethodTest2",
        "(Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodHandle;)V",
        bootstrap);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }
}
