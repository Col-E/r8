// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.DescriptorUtils;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.ASMifier;

@RunWith(Parameterized.class)
public class InvokeInterfaceOnClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public InvokeInterfaceOnClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addProgramClasses(I.class, C1.class, C2.class)
        .addProgramClassFileData(DumpMain.dump())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(getExpectedFailureMatcher(false));
  }

  @Test
  public void testR8() throws Exception {
    try {
      testForR8(parameters.getBackend())
          .addProgramClasses(I.class, C1.class, C2.class)
          .addProgramClassFileData(DumpMain.dump())
          .addKeepMainRule(Main.class)
          .setMinApi(parameters.getApiLevel())
          .compile()
          .run(parameters.getRuntime(), Main.class)
          .assertFailureWithErrorThatMatches(getExpectedFailureMatcher(true));
    } catch (CompilationFailedException e) {
      // TODO(b/144085169): The class file pipeline throws an assertion error, but should not.
      assertTrue(parameters.isCfRuntime());
    }
  }

  private Matcher<String> getExpectedFailureMatcher(boolean isR8) {
    if (parameters.getRuntime().isDex()
        && parameters
            .getRuntime()
            .asDex()
            .getVm()
            .getVersion()
            .isOlderThanOrEqual(Version.V4_4_4)) {
      return containsString("NoSuchMethodError");
    }
    if (isR8
        && parameters.getRuntime().isDex()
        && parameters
            .getRuntime()
            .asDex()
            .getVm()
            .getVersion()
            .isOlderThanOrEqual(Version.V6_0_1)) {
      // TODO(b/144085169): R8 ends up causing a code change changing the error on these runtimes.
      return containsString("NoSuchMethodError");
    }
    return containsString("IncompatibleClassChangeError");
  }

  public abstract static class I {
    public abstract void f();
  }

  public static class C1 extends I {

    @Override
    public void f() {
      System.out.println("C1::f");
    }
  }

  public static class C2 extends I {

    @Override
    public void f() {
      System.out.println("C2::f");
    }
  }

  static class Main {

    public static void main(String[] args) {
      I i = args.length % 2 == 0 ? new C1() : new C2();
      i.f();
    }
  }

  static class DumpMain implements Opcodes {

    public static void main(String[] args) throws Exception {
      ASMifier.main(
          new String[] {"-debug", ToolHelper.getClassFileForTestClass(Main.class).toString()});
    }

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_SUPER,
          DescriptorUtils.getBinaryNameFromJavaType(Main.class.getName()),
          null,
          "java/lang/Object",
          null);

      {
        methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
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
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInsn(ARRAYLENGTH);
        methodVisitor.visitInsn(ICONST_2);
        methodVisitor.visitInsn(IREM);
        Label label0 = new Label();
        methodVisitor.visitJumpInsn(IFNE, label0);
        methodVisitor.visitTypeInsn(
            NEW, "com/android/tools/r8/resolution/InvokeInterfaceOnClassTest$C1");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/resolution/InvokeInterfaceOnClassTest$C1",
            "<init>",
            "()V",
            false);
        Label label1 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label1);
        methodVisitor.visitLabel(label0);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitTypeInsn(
            NEW, "com/android/tools/r8/resolution/InvokeInterfaceOnClassTest$C2");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/resolution/InvokeInterfaceOnClassTest$C2",
            "<init>",
            "()V",
            false);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitFrame(
            Opcodes.F_SAME1,
            0,
            null,
            1,
            new Object[] {"com/android/tools/r8/resolution/InvokeInterfaceOnClassTest$I"});
        methodVisitor.visitVarInsn(ASTORE, 1);
        methodVisitor.visitVarInsn(ALOAD, 1);
        // Changed INVOKEVIRTUAL & false => INVOKEINTERFACE & true.
        methodVisitor.visitMethodInsn(
            INVOKEINTERFACE,
            "com/android/tools/r8/resolution/InvokeInterfaceOnClassTest$I",
            "f",
            "()V",
            true);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(2, 2);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
