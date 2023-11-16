// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class NonVirtualOverrideTest extends TestBase {

  private final TestParameters parameters;
  private final boolean enableVerticalClassMerging;

  static class Dimensions {

    private final Backend backend;
    private final boolean enableVerticalClassMerging;

    public Dimensions(Backend backend, boolean enableVerticalClassMerging) {
      this.backend = backend;
      this.enableVerticalClassMerging = enableVerticalClassMerging;
    }

    @Override
    public int hashCode() {
      return Objects.hash(backend, enableVerticalClassMerging);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Dimensions)) {
        return false;
      }
      Dimensions other = (Dimensions) o;
      return this.backend == other.backend
          && this.enableVerticalClassMerging == other.enableVerticalClassMerging;
    }
  }

  @Parameterized.Parameters(name = "{0}, vertical class merging: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().build(),
        BooleanUtils.values());
  }

  public NonVirtualOverrideTest(TestParameters parameters, boolean enableVerticalClassMerging) {
    this.parameters = parameters;
    this.enableVerticalClassMerging = enableVerticalClassMerging;
  }

  private static Function<Boolean, String> expectedResults =
      memoizeFunction(NonVirtualOverrideTest::getExpectedResult);

  private static Function<Dimensions, R8TestCompileResult> compilationResults =
      memoizeFunction(NonVirtualOverrideTest::compile);

  public static String getExpectedResult(boolean isOldVm) throws Exception {
    Path referenceJar = getStaticTemp().getRoot().toPath().resolve("input.jar");
    ArchiveConsumer inputConsumer = new ArchiveConsumer(referenceJar);
    inputConsumer.accept(
        ByteDataView.of(NonVirtualOverrideTestClassDump.dump()),
        DescriptorUtils.javaTypeToDescriptor(NonVirtualOverrideTestClass.class.getName()),
        null);
    inputConsumer.accept(
        ByteDataView.of(ADump.dump()),
        DescriptorUtils.javaTypeToDescriptor(A.class.getName()),
        null);
    inputConsumer.accept(
        ByteDataView.of(BDump.dump()),
        DescriptorUtils.javaTypeToDescriptor(B.class.getName()),
        null);
    inputConsumer.accept(
        ByteDataView.of(CDump.dump()),
        DescriptorUtils.javaTypeToDescriptor(C.class.getName()),
        null);
    inputConsumer.finished(null);

    return testForJvm(getStaticTemp())
        .addProgramFiles(referenceJar)
        .run(CfRuntime.getDefaultCfRuntime(), NonVirtualOverrideTestClass.class)
        .assertSuccess()
        .getStdOut();
  }

  public static boolean isDexVmBetween5_1_1and7_0_0(TestParameters parameters) {
    if (!parameters.isDexRuntime()) {
      return false;
    }
    Version version = parameters.getRuntime().asDex().getVm().getVersion();
    return version.isOlderThanOrEqual(Version.V7_0_0) && version.isNewerThanOrEqual(Version.V5_1_1);
  }

  public static R8TestCompileResult compile(Dimensions dimensions) throws Exception {
    return testForR8(getStaticTemp(), dimensions.backend)
        .addProgramClassFileData(
            NonVirtualOverrideTestClassDump.dump(), ADump.dump(), BDump.dump(), CDump.dump())
        .addKeepMainRule(NonVirtualOverrideTestClass.class)
        .addOptionsModification(
            options -> {
              options.enableClassInlining = false;
              options
                  .getVerticalClassMergerOptions()
                  .setEnabled(dimensions.enableVerticalClassMerging);
            })
        .addOptionsModification(InlinerOptions::setOnlyForceInlining)
        .setMinApi(AndroidApiLevel.B)
        .compile();
  }

  @Test
  public void test() throws Exception {
    // Run the program on Art after is has been compiled with R8.
    String referenceResult = expectedResults.apply(isDexVmBetween5_1_1and7_0_0(parameters));
    R8TestCompileResult compiled =
        compilationResults.apply(
            new Dimensions(parameters.getBackend(), enableVerticalClassMerging));
    compiled
        .run(parameters.getRuntime(), NonVirtualOverrideTestClass.class)
        .assertSuccessWithOutput(referenceResult);

    // Check that B is present and that it doesn't contain the unused private method m2.
    if (!enableVerticalClassMerging) {
      CodeInspector inspector = compiled.inspector();
      ClassSubject classSubject = inspector.clazz(B.class.getName());
      assertThat(classSubject, isPresentAndRenamed());
      assertThat(classSubject.method("void", "m2", ImmutableList.of()), isAbsent());
      assertThat(classSubject.method("void", "m3", ImmutableList.of()), isAbsent());
      assertThat(classSubject.method("void", "m4", ImmutableList.of()), isAbsent());
      assertThat(classSubject.uniqueInstanceInitializer(), isPresent());
      // The remaining method is the private method corresponding to m1 to ensure IAE.
      assertEquals(2, classSubject.allMethods().size());
    }
  }

  static class NonVirtualOverrideTestClass {

    public static void main(String[] args) {
      A a = new B();
      a.m1();
      a.m2();
      a.m3();
      a.m4();

      a = new C();
      a.m1();
      a.m2();
      a.m3();
      a.m4();

      B b = new B();
      try {
        b.m1();
      } catch (IllegalAccessError exception) {
        System.out.println("Caught IllegalAccessError when calling B.m1()");
      }
      try {
        b.m3();
      } catch (IncompatibleClassChangeError exception) {
        System.out.println("Caught IncompatibleClassChangeError when calling B.m3()");
      }

      try {
        b = new C();
        b.m1();
      } catch (IllegalAccessError exception) {
        System.out.println("Caught IllegalAccessError when calling B.m1()");
      }
      try {
        b = new C();
        b.m3();
      } catch (IncompatibleClassChangeError exception) {
        System.out.println("Caught IncompatibleClassChangeError when calling B.m3()");
      }

      C c = new C();
      c.m1();
      c.m3();
    }
  }

  static class A {

    public void m1() {
      System.out.println("In A.m1()");
    }

    public void m2() {
      System.out.println("In A.m2()");
    }

    public void m3() {
      System.out.println("In A.m3()");
    }

    public void m4() {
      System.out.println("In A.m4()");
    }
  }

  static class B extends A {

    // Made private in the dump below. This method is targeted and can therefore not be removed.
    @Override
    public void m1() {
      System.out.println("In B.m1()");
    }

    // Made private in the dump below. Ends up being dead code because the method is never called.
    @Override
    public void m2() {
      System.out.println("In B.m2()");
    }

    // Made static in the dump below. This method is targeted and can therefore not be removed.
    @Override
    public void m3() {
      System.out.println("In B.m3()");
    }

    // Made static in the dump below. Ends up being dead code because the method is never called.
    @Override
    public void m4() {
      System.out.println("In B.m4()");
    }
  }

  static class C extends B {

    @Override
    public void m1() {
      System.out.println("In C.m1()");
    }

    @Override
    public void m3() {
      System.out.println("In C.m3()");
    }
  }

  /* Below are dumps from the classes above with the changes to B as described */

  static class NonVirtualOverrideTestClassDump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_SUPER,
          "com/android/tools/r8/shaking/NonVirtualOverrideTest$NonVirtualOverrideTestClass",
          null,
          "java/lang/Object",
          null);

      classWriter.visitSource("NonVirtualOverrideTest.java", null);

      {
        methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(7, label0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(RETURN);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLocalVariable(
            "this",
            "Lcom/android/tools/r8/shaking/NonVirtualOverrideTest$NonVirtualOverrideTestClass;",
            null,
            label0,
            label1,
            0);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        Label label1 = new Label();
        Label label2 = new Label();
        methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/IllegalAccessError");
        Label label3 = new Label();
        Label label4 = new Label();
        Label label5 = new Label();
        methodVisitor.visitTryCatchBlock(
            label3, label4, label5, "java/lang/IncompatibleClassChangeError");
        Label label6 = new Label();
        Label label7 = new Label();
        Label label8 = new Label();
        methodVisitor.visitTryCatchBlock(label6, label7, label8, "java/lang/IllegalAccessError");
        Label label9 = new Label();
        Label label10 = new Label();
        Label label11 = new Label();
        methodVisitor.visitTryCatchBlock(
            label9, label10, label11, "java/lang/IncompatibleClassChangeError");
        Label label12 = new Label();
        methodVisitor.visitLabel(label12);
        methodVisitor.visitLineNumber(10, label12);
        methodVisitor.visitTypeInsn(NEW, "com/android/tools/r8/shaking/NonVirtualOverrideTest$B");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$B",
            "<init>",
            "()V",
            false);
        methodVisitor.visitVarInsn(ASTORE, 1);
        Label label13 = new Label();
        methodVisitor.visitLabel(label13);
        methodVisitor.visitLineNumber(11, label13);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$A",
            "m1",
            "()V",
            false);
        Label label14 = new Label();
        methodVisitor.visitLabel(label14);
        methodVisitor.visitLineNumber(12, label14);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$A",
            "m2",
            "()V",
            false);
        Label label15 = new Label();
        methodVisitor.visitLabel(label15);
        methodVisitor.visitLineNumber(13, label15);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$A",
            "m3",
            "()V",
            false);
        Label label16 = new Label();
        methodVisitor.visitLabel(label16);
        methodVisitor.visitLineNumber(14, label16);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$A",
            "m4",
            "()V",
            false);
        Label label17 = new Label();
        methodVisitor.visitLabel(label17);
        methodVisitor.visitLineNumber(16, label17);
        methodVisitor.visitTypeInsn(NEW, "com/android/tools/r8/shaking/NonVirtualOverrideTest$C");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$C",
            "<init>",
            "()V",
            false);
        methodVisitor.visitVarInsn(ASTORE, 1);
        Label label18 = new Label();
        methodVisitor.visitLabel(label18);
        methodVisitor.visitLineNumber(17, label18);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$A",
            "m1",
            "()V",
            false);
        Label label19 = new Label();
        methodVisitor.visitLabel(label19);
        methodVisitor.visitLineNumber(18, label19);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$A",
            "m2",
            "()V",
            false);
        Label label20 = new Label();
        methodVisitor.visitLabel(label20);
        methodVisitor.visitLineNumber(19, label20);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$A",
            "m3",
            "()V",
            false);
        Label label21 = new Label();
        methodVisitor.visitLabel(label21);
        methodVisitor.visitLineNumber(20, label21);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$A",
            "m4",
            "()V",
            false);
        Label label22 = new Label();
        methodVisitor.visitLabel(label22);
        methodVisitor.visitLineNumber(22, label22);
        methodVisitor.visitTypeInsn(NEW, "com/android/tools/r8/shaking/NonVirtualOverrideTest$B");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$B",
            "<init>",
            "()V",
            false);
        methodVisitor.visitVarInsn(ASTORE, 2);
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(24, label0);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$B",
            "m1",
            "()V",
            false);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(27, label1);
        methodVisitor.visitJumpInsn(GOTO, label3);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLineNumber(25, label2);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            3,
            new Object[] {
              "[Ljava/lang/String;",
              "com/android/tools/r8/shaking/NonVirtualOverrideTest$A",
              "com/android/tools/r8/shaking/NonVirtualOverrideTest$B"
            },
            1,
            new Object[] {"java/lang/IllegalAccessError"});
        methodVisitor.visitVarInsn(ASTORE, 3);
        Label label23 = new Label();
        methodVisitor.visitLabel(label23);
        methodVisitor.visitLineNumber(26, label23);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("Caught IllegalAccessError when calling B.m1()");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        methodVisitor.visitLabel(label3);
        methodVisitor.visitLineNumber(29, label3);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$B",
            "m3",
            "()V",
            false);
        methodVisitor.visitLabel(label4);
        methodVisitor.visitLineNumber(32, label4);
        methodVisitor.visitJumpInsn(GOTO, label6);
        methodVisitor.visitLabel(label5);
        methodVisitor.visitLineNumber(30, label5);
        methodVisitor.visitFrame(
            Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/IncompatibleClassChangeError"});
        methodVisitor.visitVarInsn(ASTORE, 3);
        Label label24 = new Label();
        methodVisitor.visitLabel(label24);
        methodVisitor.visitLineNumber(31, label24);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("Caught IncompatibleClassChangeError when calling B.m3()");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        methodVisitor.visitLabel(label6);
        methodVisitor.visitLineNumber(35, label6);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitTypeInsn(NEW, "com/android/tools/r8/shaking/NonVirtualOverrideTest$C");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$C",
            "<init>",
            "()V",
            false);
        methodVisitor.visitVarInsn(ASTORE, 2);
        Label label25 = new Label();
        methodVisitor.visitLabel(label25);
        methodVisitor.visitLineNumber(36, label25);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$B",
            "m1",
            "()V",
            false);
        methodVisitor.visitLabel(label7);
        methodVisitor.visitLineNumber(39, label7);
        methodVisitor.visitJumpInsn(GOTO, label9);
        methodVisitor.visitLabel(label8);
        methodVisitor.visitLineNumber(37, label8);
        methodVisitor.visitFrame(
            Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/IllegalAccessError"});
        methodVisitor.visitVarInsn(ASTORE, 3);
        Label label26 = new Label();
        methodVisitor.visitLabel(label26);
        methodVisitor.visitLineNumber(38, label26);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("Caught IllegalAccessError when calling B.m1()");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        methodVisitor.visitLabel(label9);
        methodVisitor.visitLineNumber(41, label9);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitTypeInsn(NEW, "com/android/tools/r8/shaking/NonVirtualOverrideTest$C");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$C",
            "<init>",
            "()V",
            false);
        methodVisitor.visitVarInsn(ASTORE, 2);
        Label label27 = new Label();
        methodVisitor.visitLabel(label27);
        methodVisitor.visitLineNumber(42, label27);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$B",
            "m3",
            "()V",
            false);
        methodVisitor.visitLabel(label10);
        methodVisitor.visitLineNumber(45, label10);
        Label label28 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label28);
        methodVisitor.visitLabel(label11);
        methodVisitor.visitLineNumber(43, label11);
        methodVisitor.visitFrame(
            Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/IncompatibleClassChangeError"});
        methodVisitor.visitVarInsn(ASTORE, 3);
        Label label29 = new Label();
        methodVisitor.visitLabel(label29);
        methodVisitor.visitLineNumber(44, label29);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("Caught IncompatibleClassChangeError when calling B.m3()");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        methodVisitor.visitLabel(label28);
        methodVisitor.visitLineNumber(47, label28);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitTypeInsn(NEW, "com/android/tools/r8/shaking/NonVirtualOverrideTest$C");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$C",
            "<init>",
            "()V",
            false);
        methodVisitor.visitVarInsn(ASTORE, 3);
        Label label30 = new Label();
        methodVisitor.visitLabel(label30);
        methodVisitor.visitLineNumber(48, label30);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$C",
            "m1",
            "()V",
            false);
        Label label31 = new Label();
        methodVisitor.visitLabel(label31);
        methodVisitor.visitLineNumber(49, label31);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$C",
            "m3",
            "()V",
            false);
        Label label32 = new Label();
        methodVisitor.visitLabel(label32);
        methodVisitor.visitLineNumber(50, label32);
        methodVisitor.visitInsn(RETURN);
        Label label33 = new Label();
        methodVisitor.visitLabel(label33);
        methodVisitor.visitLocalVariable(
            "exception", "Ljava/lang/IllegalAccessError;", null, label23, label3, 3);
        methodVisitor.visitLocalVariable(
            "exception", "Ljava/lang/IncompatibleClassChangeError;", null, label24, label6, 3);
        methodVisitor.visitLocalVariable(
            "exception", "Ljava/lang/IllegalAccessError;", null, label26, label9, 3);
        methodVisitor.visitLocalVariable(
            "exception", "Ljava/lang/IncompatibleClassChangeError;", null, label29, label28, 3);
        methodVisitor.visitLocalVariable("args", "[Ljava/lang/String;", null, label12, label33, 0);
        methodVisitor.visitLocalVariable(
            "a",
            "Lcom/android/tools/r8/shaking/NonVirtualOverrideTest$A;",
            null,
            label13,
            label33,
            1);
        methodVisitor.visitLocalVariable(
            "b",
            "Lcom/android/tools/r8/shaking/NonVirtualOverrideTest$B;",
            null,
            label0,
            label33,
            2);
        methodVisitor.visitLocalVariable(
            "c",
            "Lcom/android/tools/r8/shaking/NonVirtualOverrideTest$C;",
            null,
            label30,
            label33,
            3);
        methodVisitor.visitMaxs(2, 4);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }

  static class ADump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_SUPER,
          "com/android/tools/r8/shaking/NonVirtualOverrideTest$A",
          null,
          "java/lang/Object",
          null);

      classWriter.visitSource("NonVirtualOverrideTest.java", null);

      {
        methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(53, label0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(RETURN);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLocalVariable(
            "this",
            "Lcom/android/tools/r8/shaking/NonVirtualOverrideTest$A;",
            null,
            label0,
            label1,
            0);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "m1", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(56, label0);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("In A.m1()");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(57, label1);
        methodVisitor.visitInsn(RETURN);
        Label label2 = new Label();
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLocalVariable(
            "this",
            "Lcom/android/tools/r8/shaking/NonVirtualOverrideTest$A;",
            null,
            label0,
            label2,
            0);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "m2", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(60, label0);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("In A.m2()");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(61, label1);
        methodVisitor.visitInsn(RETURN);
        Label label2 = new Label();
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLocalVariable(
            "this",
            "Lcom/android/tools/r8/shaking/NonVirtualOverrideTest$A;",
            null,
            label0,
            label2,
            0);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "m3", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(64, label0);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("In A.m3()");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(65, label1);
        methodVisitor.visitInsn(RETURN);
        Label label2 = new Label();
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLocalVariable(
            "this",
            "Lcom/android/tools/r8/shaking/NonVirtualOverrideTest$A;",
            null,
            label0,
            label2,
            0);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "m4", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(68, label0);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("In A.m4()");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(69, label1);
        methodVisitor.visitInsn(RETURN);
        Label label2 = new Label();
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLocalVariable(
            "this",
            "Lcom/android/tools/r8/shaking/NonVirtualOverrideTest$A;",
            null,
            label0,
            label2,
            0);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }

  static class BDump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_SUPER,
          "com/android/tools/r8/shaking/NonVirtualOverrideTest$B",
          null,
          "com/android/tools/r8/shaking/NonVirtualOverrideTest$A",
          null);

      classWriter.visitSource("NonVirtualOverrideTest.java", null);

      {
        methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(72, label0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$A",
            "<init>",
            "()V",
            false);
        methodVisitor.visitInsn(RETURN);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLocalVariable(
            "this",
            "Lcom/android/tools/r8/shaking/NonVirtualOverrideTest$B;",
            null,
            label0,
            label1,
            0);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "m1", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(76, label0);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("In B.m1()");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(77, label1);
        methodVisitor.visitInsn(RETURN);
        Label label2 = new Label();
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLocalVariable(
            "this",
            "Lcom/android/tools/r8/shaking/NonVirtualOverrideTest$B;",
            null,
            label0,
            label2,
            0);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "m2", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(81, label0);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("In B.m2()");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(82, label1);
        methodVisitor.visitInsn(RETURN);
        Label label2 = new Label();
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLocalVariable(
            "this",
            "Lcom/android/tools/r8/shaking/NonVirtualOverrideTest$B;",
            null,
            label0,
            label2,
            0);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_STATIC, "m3", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(86, label0);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("In B.m3()");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(87, label1);
        methodVisitor.visitInsn(RETURN);
        Label label2 = new Label();
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLocalVariable(
            "this",
            "Lcom/android/tools/r8/shaking/NonVirtualOverrideTest$B;",
            null,
            label0,
            label2,
            0);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_STATIC, "m4", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(91, label0);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("In B.m4()");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(92, label1);
        methodVisitor.visitInsn(RETURN);
        Label label2 = new Label();
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLocalVariable(
            "this",
            "Lcom/android/tools/r8/shaking/NonVirtualOverrideTest$B;",
            null,
            label0,
            label2,
            0);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }

  static class CDump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_SUPER,
          "com/android/tools/r8/shaking/NonVirtualOverrideTest$C",
          null,
          "com/android/tools/r8/shaking/NonVirtualOverrideTest$B",
          null);

      classWriter.visitSource("NonVirtualOverrideTest.java", null);

      {
        methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(95, label0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/shaking/NonVirtualOverrideTest$B",
            "<init>",
            "()V",
            false);
        methodVisitor.visitInsn(RETURN);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLocalVariable(
            "this",
            "Lcom/android/tools/r8/shaking/NonVirtualOverrideTest$C;",
            null,
            label0,
            label1,
            0);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "m1", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(98, label0);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("In C.m1()");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(99, label1);
        methodVisitor.visitInsn(RETURN);
        Label label2 = new Label();
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLocalVariable(
            "this",
            "Lcom/android/tools/r8/shaking/NonVirtualOverrideTest$C;",
            null,
            label0,
            label2,
            0);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "m3", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(102, label0);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("In C.m3()");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(103, label1);
        methodVisitor.visitInsn(RETURN);
        Label label2 = new Label();
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLocalVariable(
            "this",
            "Lcom/android/tools/r8/shaking/NonVirtualOverrideTest$C;",
            null,
            label0,
            label2,
            0);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
