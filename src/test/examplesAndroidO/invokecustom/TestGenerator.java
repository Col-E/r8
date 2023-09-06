// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package invokecustom;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
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

// TODO(b/167145686): Migrate these tests to the new setup ala
//  InvokeDynamicVirtualDispatchToDefaultInterfaceMethodTest
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
    try (InputStream input = Files.newInputStream(classNamePath)) {
      ClassReader cr = new ClassReader(input);
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
      cr.accept(
          new ClassVisitor(Opcodes.ASM7, cw) {
            @Override
            public void visitEnd() {
              generateMethodTest1(cw);
              generateMethodTest2(cw);
              generateMethodTest3(cw);
              generateMethodTest4(cw);
              generateMethodTest5(cw);
              generateMethodTest6(cw);
              generateMethodTest7(cw);
              generateMethodTest8(cw);
              generateMethodTest10(cw);
              generateMethodTest11(cw);
              generateMethodTest12(cw);
              generateMethodTest13(cw);
              generateMethodTest14(cw);
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

  /* generate main method that only call all test methods. */
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
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, Type.getInternalName(InvokeCustom.class), "test5", "()V", false);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, Type.getInternalName(InvokeCustom.class), "test6", "()V", false);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, Type.getInternalName(InvokeCustom.class), "test7", "()V", false);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, Type.getInternalName(InvokeCustom.class), "test8", "()V", false);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, Type.getInternalName(InvokeCustom.class), "test10", "()V", false);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, Type.getInternalName(InvokeCustom.class), "test11", "()V", false);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, Type.getInternalName(InvokeCustom.class), "test12", "()V", false);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, Type.getInternalName(InvokeCustom.class), "test13", "()V", false);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC, Type.getInternalName(InvokeCustom.class), "test14", "()V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   *  Generate test with an invokedynamic, a static bootstrap method without extra args and no arg
   *  to the target method.
   */
  private void generateMethodTest1(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test1", "()V",
            null, null);
    MethodType mt =
        MethodType.methodType(
            CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class);
    Handle bootstrap = new Handle( Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmLookupStatic", mt.toMethodDescriptorString(), false);
    mv.visitInvokeDynamicInsn("targetMethodTest1", "()V", bootstrap);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   *  Generate test with an invokedynamic, a static bootstrap method without extra args and
   *  args to the target method.
   */
  private void generateMethodTest2(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test2", "()V",
        null, null);
    MethodType mt = MethodType.methodType(
            CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class);
    Handle bootstrap = new Handle( Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmLookupStatic", mt.toMethodDescriptorString(), false);
    mv.visitLdcInsn(new Boolean(true));
    mv.visitLdcInsn(new Byte((byte) 127));
    mv.visitLdcInsn(new Character('c'));
    mv.visitLdcInsn(new Short((short) 1024));
    mv.visitLdcInsn(new Integer(123456));
    mv.visitLdcInsn(new Float(1.2f));
    mv.visitLdcInsn(new Long(123456789));
    mv.visitLdcInsn(new Double(3.5123456789));
    mv.visitLdcInsn("String");
    mv.visitInvokeDynamicInsn("targetMethodTest2", "(ZBCSIFJDLjava/lang/String;)V", bootstrap);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   *  Generate test with an invokedynamic, a static bootstrap method with extra args and no arg
   *  to the target method.
   */
  private void generateMethodTest3(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test3", "()V",
        null, null);
    MethodType mt = MethodType.methodType(
            CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class, int.class,
        long.class, float.class, double.class);
    Handle bootstrap = new Handle( Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmLookupStaticWithExtraArgs", mt.toMethodDescriptorString(), false);
    mv.visitInvokeDynamicInsn("targetMethodTest3", "()V", bootstrap, new Integer(1),
        new Long(123456789), new Float(123.456), new Double(123456.789123));
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   *  Generate test with an invokedynamic, a static bootstrap method with an extra arg that is a
   *  MethodHandle of kind invokespecial.
   */
  private void generateMethodTest4(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test4", "()V",
        null, null);
    MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
        MethodType.class, MethodHandle.class);
    Handle bootstrap = new Handle( Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmCreateCallSite", mt.toMethodDescriptorString(), false);
    mv.visitTypeInsn(Opcodes.NEW, Type.getInternalName(InvokeCustom.class));
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL, Type.getInternalName(InvokeCustom.class), "<init>", "()V", false);
    mv.visitInvokeDynamicInsn("targetMethodTest5", "(Linvokecustom/InvokeCustom;)V", bootstrap,
        new Handle( Opcodes.H_INVOKESPECIAL, Type.getInternalName(Super.class),
            "targetMethodTest5", "()V", false));
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   *  Generate test with an invokedynamic, a static bootstrap method with an extra arg that is a
   *  MethodHandle of kind invoke interface. The target method is a default method into an interface
   *  that shadows another default method from a super interface.
   */
  private void generateMethodTest5(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test5", "()V",
        null, null);
    MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
        MethodType.class, MethodHandle.class);
    Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmCreateCallSite", mt.toMethodDescriptorString(), false);
    mv.visitTypeInsn(Opcodes.NEW, Type.getInternalName(InvokeCustom.class));
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL, Type.getInternalName(InvokeCustom.class), "<init>", "()V", false);
    mv.visitInvokeDynamicInsn("targetMethodTest6", "(Linvokecustom/I;)V", bootstrap,
        new Handle(Opcodes.H_INVOKEINTERFACE, Type.getInternalName(I.class),
            "targetMethodTest6", "()V", true));
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   *  Generate test with an invokedynamic, a static bootstrap method with an extra arg that is a
   *  MethodHandle of kind invoke interface. The target method is a default method into an interface
   *  that is at the end of a chain of interfaces.
   */
  private void generateMethodTest6(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test6", "()V",
        null, null);
    MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
        MethodType.class, MethodHandle.class);
    Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmCreateCallSite", mt.toMethodDescriptorString(), false);
    mv.visitTypeInsn(Opcodes.NEW, Type.getInternalName(InvokeCustom.class));
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL, Type.getInternalName(InvokeCustom.class), "<init>", "()V", false);
    mv.visitInvokeDynamicInsn("targetMethodTest7", "(Linvokecustom/J;)V", bootstrap,
        new Handle(Opcodes.H_INVOKEINTERFACE, Type.getInternalName(J.class),
            "targetMethodTest7", "()V", true));
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   *  Generate test with an invokedynamic, a static bootstrap method with an extra arg that is a
   *  MethodHandle of kind invoke interface. The target method is a method into an interface
   *  that is shadowed by another definition into a sub interfaces.
   */
  private void generateMethodTest7(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test7", "()V",
        null, null);
    MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
        MethodType.class, MethodHandle.class);
    Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmCreateCallSite", mt.toMethodDescriptorString(), false);
    mv.visitTypeInsn(Opcodes.NEW, Type.getInternalName(InvokeCustom.class));
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL, Type.getInternalName(InvokeCustom.class), "<init>", "()V", false);
    mv.visitInvokeDynamicInsn("targetMethodTest8", "(Linvokecustom/J;)V", bootstrap,
        new Handle(Opcodes.H_INVOKEINTERFACE, Type.getInternalName(J.class),
            "targetMethodTest8", "()V", true));
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   *  Generate test with an invokedynamic, a static bootstrap method with an extra arg that is a
   *  MethodHandle of kind invoke virtual. The target method is a method into an interface that is
   *  not shadowed by an implementation into a classes implementing the interface.
   */
  private void generateMethodTest8(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test8", "()V",
        null, null);
    MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
        MethodType.class, MethodHandle.class);
    Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmCreateCallSite", mt.toMethodDescriptorString(), false);
    mv.visitTypeInsn(Opcodes.NEW, Type.getInternalName(InvokeCustom.class));
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL, Type.getInternalName(InvokeCustom.class), "<init>", "()V", false);
    mv.visitInvokeDynamicInsn("targetMethodTest9", "(Linvokecustom/InvokeCustom;)V", bootstrap,
        new Handle(Opcodes.H_INVOKEVIRTUAL, Type.getInternalName(InvokeCustom.class),
            "targetMethodTest9", "()V", false));
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   *  Generate test with an invokedynamic, a static bootstrap method with an extra arg that is a
   *  MethodHandle of kind get static. The method handle read a static field from a class.
   */
  private void generateMethodTest10(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test10", "()V",
        null, null);
    MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
        MethodType.class, MethodHandle.class);
    Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmCreateCallSite", mt.toMethodDescriptorString(), false);
    mv.visitFieldInsn(Opcodes.GETSTATIC,
        "java/lang/System",
        "out",
        "Ljava/io/PrintStream;");
    mv.visitInvokeDynamicInsn("staticField1", "()Ljava/lang/String;", bootstrap,
        new Handle(Opcodes.H_GETSTATIC, Type.getInternalName(InvokeCustom.class),
            "staticField1", "Ljava/lang/String;", false));
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
        "java/io/PrintStream",
        "println",
        "(Ljava/lang/String;)V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   * Generate test with an invokedynamic, a static bootstrap method with an extra arg that is a
   * MethodHandle of kind put static. The method handle write a static field in a class and then
   * print its value.
   */
  private void generateMethodTest11(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test11", "()V",
        null, null);
    MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
            MethodType.class, MethodHandle.class);
    Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmCreateCallSite", mt.toMethodDescriptorString(), false);
    mv.visitLdcInsn("Write static field");
    mv.visitInvokeDynamicInsn("staticField1", "(Ljava/lang/String;)V", bootstrap,
        new Handle(Opcodes.H_PUTSTATIC, Type.getInternalName(InvokeCustom.class),
            "staticField1", "Ljava/lang/String;", false));
    mv.visitFieldInsn(Opcodes.GETSTATIC,
        "java/lang/System",
        "out",
        "Ljava/io/PrintStream;");
    mv.visitFieldInsn(Opcodes.GETSTATIC,
        Type.getInternalName(InvokeCustom.class),
        "staticField1",
        "Ljava/lang/String;");
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
        "java/io/PrintStream",
        "println",
        "(Ljava/lang/String;)V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   *  Generate test with an invokedynamic, a static bootstrap method with an extra arg that is a
   *  MethodHandle of kind get instance. The method handle read an instance field from a class.
   */
  private void generateMethodTest12(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test12", "()V",
        null, null);
    MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
        MethodType.class, MethodHandle.class);
    Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmCreateCallSite", mt.toMethodDescriptorString(), false);
    mv.visitFieldInsn(Opcodes.GETSTATIC,
        "java/lang/System",
        "out",
        "Ljava/io/PrintStream;");
    mv.visitTypeInsn(Opcodes.NEW, Type.getInternalName(InvokeCustom.class));
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL, Type.getInternalName(InvokeCustom.class), "<init>", "()V", false);
    mv.visitInvokeDynamicInsn("instanceField1", "(Linvokecustom/InvokeCustom;)Ljava/lang/String;",
        bootstrap, new Handle(Opcodes.H_GETFIELD, Type.getInternalName(InvokeCustom.class),
            "instanceField1", "Ljava/lang/String;", false));
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
        "java/io/PrintStream",
        "println",
        "(Ljava/lang/String;)V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   * Generate test with an invokedynamic, a static bootstrap method with an extra arg that is a
   * MethodHandle of kind put instance. The method handle write an instance field in a class and
   * then print its value.
   */
  private void generateMethodTest13(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test13", "()V",
        null, null);
    MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
        MethodType.class, MethodHandle.class);
    Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmCreateCallSite", mt.toMethodDescriptorString(), false);
    mv.visitTypeInsn(Opcodes.NEW, Type.getInternalName(InvokeCustom.class));
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL, Type.getInternalName(InvokeCustom.class), "<init>", "()V", false);
    mv.visitVarInsn(Opcodes.ASTORE, 0);
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitLdcInsn("Write instance field");
    mv.visitInvokeDynamicInsn("instanceField1", "(Linvokecustom/InvokeCustom;Ljava/lang/String;)V",
        bootstrap, new Handle(Opcodes.H_PUTFIELD, Type.getInternalName(InvokeCustom.class),
            "instanceField1", "Ljava/lang/String;", false));
    mv.visitFieldInsn(Opcodes.GETSTATIC,
        "java/lang/System",
        "out",
        "Ljava/io/PrintStream;");
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitFieldInsn(Opcodes.GETFIELD,
        Type.getInternalName(InvokeCustom.class),
        "instanceField1",
        "Ljava/lang/String;");
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
        "java/io/PrintStream",
        "println",
        "(Ljava/lang/String;)V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }

  /**
   * Generate test with an invokedynamic, a static bootstrap method with an extra arg that is a
   * MethodHandle of kind invoke instance taking an argument and returning a result. This should
   * work through renaming.
   */
  private void generateMethodTest14(ClassVisitor cv) {
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "test14", "()V",
        null, null);
    MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
        MethodType.class, MethodHandle.class);
    Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(InvokeCustom.class),
        "bsmCreateCallSite", mt.toMethodDescriptorString(), false);
    mv.visitTypeInsn(Opcodes.NEW, Type.getInternalName(InvokeCustom.class));
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL, Type.getInternalName(InvokeCustom.class), "<init>", "()V", false);
    mv.visitTypeInsn(Opcodes.NEW, Type.getInternalName(ClassWithLongName.class));
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        Type.getInternalName(ClassWithLongName.class),
        "<init>",
        "()V",
        false);
    mv.visitInvokeDynamicInsn(
        "targetMethodTest11",
        "(Linvokecustom/InvokeCustom;Linvokecustom/ClassWithLongName;)"
            + "Linvokecustom/AnotherClassWithALongName;",
        bootstrap,
        new Handle(
            Opcodes.H_INVOKEVIRTUAL,
            Type.getInternalName(InvokeCustom.class),
            "targetMethodTest11",
            "(Linvokecustom/ClassWithLongName;)Linvokecustom/AnotherClassWithALongName;",
            false));
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
  }
}
