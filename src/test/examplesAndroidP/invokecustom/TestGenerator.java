// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package invokecustom;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class TestGenerator {

  private final Path classNamePath;
  private final Path outputClassNamePath;

  public static void main(String[] args) throws IOException {
    assert args.length == 2;
    String fileName = InvokeCustom.class.getSimpleName() + ".class";
    Path inputFile = Paths.get(args[0], TestGenerator.class.getPackage().getName(), fileName);
    Path outputFile = Paths.get(args[1], fileName);
    TestGenerator testGenerator = new TestGenerator(inputFile, outputFile);
    testGenerator.generateTests();
  }

  public TestGenerator(Path classNamePath, Path outputClassNamePath) {
    this.classNamePath = classNamePath;
    this.outputClassNamePath = outputClassNamePath;
  }

  private void generateTests() throws IOException {
    Files.createDirectories(outputClassNamePath.getParent());
    try (InputStream inputStream = Files.newInputStream(classNamePath)) {
      ClassReader cr = new ClassReader(inputStream);
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
      cr.accept(
          new ClassVisitor(Opcodes.ASM7, cw) {
            @Override
            public void visitEnd() {
              generateMethodTest1(cw);
              generateMethodTest2(cw);
              generateMethodTest3(cw);
              generateMethodTest4(cw);
              generateMethodMain(cw);
              super.visitEnd();
            }
          }, 0);
      try (OutputStream output =
          Files.newOutputStream(outputClassNamePath, StandardOpenOption.CREATE)) {
        output.write(cw.toByteArray());
      }
    }
  }

  /* Generate main method that only call all test methods. */
  private void generateMethodMain(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(
            Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, Type.getInternalName(InvokeCustom.class), "test1", "()V", false);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, Type.getInternalName(InvokeCustom.class), "test2", "()V", false);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, Type.getInternalName(InvokeCustom.class), "test3", "()V", false);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, Type.getInternalName(InvokeCustom.class), "test4", "()V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   * Generate test with an invokedynamic, a static bootstrap method without extra args and
   * args to the target method.
   */
  private void generateMethodTest1(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test1", "()V",
        null, null);
    MethodType mt = MethodType.methodType(
            CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class);
    Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
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

  /**
   * Generate test with an invokedynamic, a static bootstrap method without extra args and
   * args to the target method.
   */
  private void generateMethodTest2(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test2", "()V",
        null, null);
    MethodType mt = MethodType.methodType(
        CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class);
    Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmLookupStatic", mt.toMethodDescriptorString(), false);
    mv.visitLdcInsn(Type.getMethodType("(ZBSCIFJDLjava/lang/String;)Ljava/lang/Object;"));
    mv.visitInvokeDynamicInsn("targetMethodTest3", "(Ljava/lang/invoke/MethodType;)V",
        bootstrap);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   * Generate test with a const method handle pointing to the middle of a class hierarchy.
   * Call a static method with the method handle which will do a MethodHandle.invoke call on
   * a sub class instance.
   *
   * Tests that the const method handle is rewritten when renaming. Also tests that the
   * middle class does not disappear (for instance via class merging).
   */
  private void generateMethodTest3(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test3", "()V",
        null, null);
    MethodType mt = MethodType.methodType(ReturnType.class, ArgumentType.class);
    Handle invokeWithArg = new Handle(
        Opcodes.H_INVOKEVIRTUAL,
        Type.getInternalName(Middle.class),
        "targetMethodTest4",
        mt.toMethodDescriptorString(),
        false);
    mv.visitLdcInsn(invokeWithArg);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        Type.getInternalName(InvokeCustom.class),
        "doInvokeSubWithArg",
        "(Ljava/lang/invoke/MethodHandle;)V",
        false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   * Generate test with a const method handle pointing to a class which inherits the method from
   * the super class. Call a static method with the method handle which will do a
   * MethodHandle.invokeExact.
   */
  private void generateMethodTest4(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test4", "()V",
        null, null);
    MethodType mt = MethodType.methodType(ReturnType.class, ArgumentType.class);
    Handle invokeExactWithArg = new Handle(
        Opcodes.H_INVOKEVIRTUAL,
        Type.getInternalName(Impl.class),
        "targetMethodTest5",
        mt.toMethodDescriptorString(),
        false);
    mv.visitLdcInsn(invokeExactWithArg);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        Type.getInternalName(InvokeCustom.class),
        "doInvokeExactImplWithArg",
        "(Ljava/lang/invoke/MethodHandle;)V",
        false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }
}
