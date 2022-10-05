// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.rewrite.assertions.testclasses.TestClassForInnerClass;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

@RunWith(Parameterized.class)
public class AssertionsConfigurationTest extends TestBase implements Opcodes {

  private final TestParameters parameters;

  private Class<?> class1 = com.android.tools.r8.rewrite.assertions.testclasses.Class1.class;
  private Class<?> class2 = com.android.tools.r8.rewrite.assertions.testclasses.Class2.class;
  private Class<?> subpackageClass1 =
      com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class1.class;
  private Class<?> subpackageClass2 =
      com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class2.class;

  private List<Class<?>> testClasses =
      ImmutableList.of(TestClass.class, class1, class2, subpackageClass1, subpackageClass2);

  private String packageName =
      com.android.tools.r8.rewrite.assertions.testclasses.Class1.class.getPackage().getName();
  private String subPackageName =
      com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class1.class
          .getPackage()
          .getName();

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public AssertionsConfigurationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void runD8Test(
      ThrowableConsumer<D8TestBuilder> builderConsumer,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector,
      List<String> outputLines)
      throws Exception {
    testForD8()
        .addProgramClasses(testClasses)
        .setMinApi(parameters.getApiLevel())
        .apply(builderConsumer)
        .compile()
        .inspect(inspector)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(StringUtils.lines(outputLines));
  }

  public void runR8Test(
      ThrowableConsumer<R8FullTestBuilder> builderConsumer,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector,
      List<String> outputLines)
      throws Exception {
    runR8Test(builderConsumer, inspector, outputLines, false);
  }

  public void runR8Test(
      ThrowableConsumer<R8FullTestBuilder> builderConsumer,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector,
      List<String> outputLines,
      boolean enableJvmAssertions)
      throws Exception {

    testForR8(parameters.getBackend())
        .addProgramClasses(testClasses)
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(class1, class2, subpackageClass1, subpackageClass2)
        .setMinApi(parameters.getApiLevel())
        .apply(builderConsumer)
        .compile()
        .inspect(inspector)
        .enableRuntimeAssertions(enableJvmAssertions)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(StringUtils.lines(outputLines));
  }

  private List<String> allAssertionsExpectedLines() {
    return ImmutableList.of(
        "AssertionError in testclasses.Class1",
        "AssertionError in testclasses.Class2",
        "AssertionError in testclasses.subpackage.Class1",
        "AssertionError in testclasses.subpackage.Class2",
        "DONE");
  }

  private List<String> noAllAssertionsExpectedLines() {
    return ImmutableList.of("DONE");
  }

  private void checkAssertionCodeRemoved(ClassSubject subject) {
    assertThat(subject, isPresent());
    // <clinit> is removed by R8 as it becomes empty.
    if (subject.uniqueMethodWithOriginalName("<clinit>").isPresent()) {
      assertFalse(
          subject
              .uniqueMethodWithOriginalName("<clinit>")
              .streamInstructions()
              .anyMatch(InstructionSubject::isStaticPut));
    }
    assertFalse(
        subject
            .uniqueMethodWithOriginalName("m")
            .streamInstructions()
            .anyMatch(InstructionSubject::isThrow));
  }

  private void checkAssertionCodeRemoved(CodeInspector inspector, Class<?> clazz) {
    checkAssertionCodeRemoved(inspector.clazz(clazz));
  }

  private void checkAssertionCodeEnabled(CodeInspector inspector, Class<?> clazz) {
    AssertionsCheckerUtils.checkAssertionCodeEnabled(inspector.clazz(clazz), "m");
  }

  private void checkAssertionCodeLeft(CodeInspector inspector, Class<?> clazz) {
    ClassSubject subject = inspector.clazz(clazz);
    assertThat(subject, isPresent());
    assertTrue(
        subject
            .uniqueMethodWithOriginalName("<clinit>")
            .streamInstructions()
            .anyMatch(InstructionSubject::isStaticPut));
    assertTrue(
        subject
            .uniqueMethodWithOriginalName("m")
            .streamInstructions()
            .anyMatch(InstructionSubject::isThrow));
  }

  private void checkAssertionCodeRemoved(CodeInspector inspector) {
    checkAssertionCodeRemoved(inspector, class1);
    checkAssertionCodeRemoved(inspector, class2);
    checkAssertionCodeRemoved(inspector, subpackageClass1);
    checkAssertionCodeRemoved(inspector, subpackageClass2);
  }

  private void checkAssertionCodeEnabled(CodeInspector inspector) {
    checkAssertionCodeEnabled(inspector, class1);
    checkAssertionCodeEnabled(inspector, class2);
    checkAssertionCodeEnabled(inspector, subpackageClass1);
    checkAssertionCodeEnabled(inspector, subpackageClass2);
  }

  private void checkAssertionCodeLeft(CodeInspector inspector) {
    checkAssertionCodeLeft(inspector, class1);
    checkAssertionCodeLeft(inspector, class2);
    checkAssertionCodeLeft(inspector, subpackageClass1);
    checkAssertionCodeLeft(inspector, subpackageClass2);
  }

  @Test
  public void testAssertionsForDex() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    // Leaving assertions in or disabling them on Dalvik/Art means no assertions.
    runD8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::passthroughAllAssertions),
        this::checkAssertionCodeLeft,
        noAllAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::passthroughAllAssertions),
        this::checkAssertionCodeLeft,
        noAllAssertionsExpectedLines());
    runD8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeDisableAllAssertions),
        this::checkAssertionCodeRemoved,
        noAllAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeDisableAllAssertions),
        this::checkAssertionCodeRemoved,
        noAllAssertionsExpectedLines());
    // Compile time enabling assertions gives assertions on Dalvik/Art.
    runD8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeEnableAllAssertions),
        this::checkAssertionCodeEnabled,
        allAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeEnableAllAssertions),
        this::checkAssertionCodeEnabled,
        allAssertionsExpectedLines());
    // Enabling for the package should enable all.
    runD8Test(
        builder ->
            builder.addAssertionsConfiguration(
                b -> b.setCompileTimeEnable().setScopePackage(packageName).build()),
        this::checkAssertionCodeEnabled,
        allAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                b -> b.setCompileTimeEnable().setScopePackage(packageName).build()),
        this::checkAssertionCodeEnabled,
        allAssertionsExpectedLines());
  }

  @Test
  public void testAssertionsForCf() throws Exception {
    Assume.assumeTrue(parameters.isCfRuntime());
    // Leaving assertion code means assertions are controlled by the -ea flag.
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::passthroughAllAssertions),
        this::checkAssertionCodeLeft,
        noAllAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::passthroughAllAssertions),
        this::checkAssertionCodeLeft,
        allAssertionsExpectedLines(),
        true);
    // Compile time enabling or disabling assertions means the -ea flag has no effect.
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeEnableAllAssertions),
        this::checkAssertionCodeEnabled,
        allAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeEnableAllAssertions),
        this::checkAssertionCodeEnabled,
        allAssertionsExpectedLines(),
        true);
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeDisableAllAssertions),
        this::checkAssertionCodeRemoved,
        noAllAssertionsExpectedLines());
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                AssertionsConfiguration.Builder::compileTimeDisableAllAssertions),
        this::checkAssertionCodeRemoved,
        noAllAssertionsExpectedLines(),
        true);
  }

  @Test
  public void testEnableForPackageForDex() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    runD8Test(
        builder ->
            builder.addAssertionsConfiguration(
                b -> b.setCompileTimeEnable().setScopePackage(subPackageName).build()),
        inspector -> {
          checkAssertionCodeEnabled(inspector, subpackageClass1);
          checkAssertionCodeEnabled(inspector, subpackageClass2);
        },
        ImmutableList.of(
            "AssertionError in testclasses.subpackage.Class1",
            "AssertionError in testclasses.subpackage.Class2",
            "DONE"));
    runR8Test(
        builder ->
            builder.addAssertionsConfiguration(
                b -> b.setCompileTimeEnable().setScopePackage(subPackageName).build()),
        inspector -> {
          checkAssertionCodeEnabled(inspector, subpackageClass1);
          checkAssertionCodeEnabled(inspector, subpackageClass2);
        },
        ImmutableList.of(
            "AssertionError in testclasses.subpackage.Class1",
            "AssertionError in testclasses.subpackage.Class2",
            "DONE"));
  }

  @Test
  public void testEnableForClassForDex() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    runD8Test(
        builder ->
            builder
                .addAssertionsConfiguration(
                    b -> b.setCompileTimeEnable().setScopeClass(class1.getCanonicalName()).build())
                .addAssertionsConfiguration(
                    b ->
                        b.setCompileTimeEnable()
                            .setScopeClass(subpackageClass2.getCanonicalName())
                            .build()),
        inspector -> {
          checkAssertionCodeEnabled(inspector, class1);
          checkAssertionCodeEnabled(inspector, subpackageClass2);
        },
        ImmutableList.of(
            "AssertionError in testclasses.Class1",
            "AssertionError in testclasses.subpackage.Class2",
            "DONE"));
    runR8Test(
        builder ->
            builder
                .addAssertionsConfiguration(
                    b -> b.setCompileTimeEnable().setScopeClass(class1.getCanonicalName()).build())
                .addAssertionsConfiguration(
                    b ->
                        b.setCompileTimeEnable()
                            .setScopeClass(subpackageClass2.getCanonicalName())
                            .build()),
        inspector -> {
          checkAssertionCodeEnabled(inspector, class1);
          checkAssertionCodeEnabled(inspector, subpackageClass2);
        },
        ImmutableList.of(
            "AssertionError in testclasses.Class1",
            "AssertionError in testclasses.subpackage.Class2",
            "DONE"));
  }

  @Test
  public void testMixedForDex() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    runD8Test(
        builder ->
            builder
                .addAssertionsConfiguration(
                    b -> b.setCompileTimeEnable().setScopePackage(packageName).build())
                .addAssertionsConfiguration(
                    b -> b.setCompileTimeDisable().setScopeClass(class2.getCanonicalName()).build())
                .addAssertionsConfiguration(
                    b ->
                        b.setCompileTimeDisable()
                            .setScopeClass(subpackageClass1.getCanonicalName())
                            .build()),
        inspector -> {
          checkAssertionCodeEnabled(inspector, class1);
          checkAssertionCodeRemoved(inspector, class2);
          checkAssertionCodeRemoved(inspector, subpackageClass1);
          checkAssertionCodeEnabled(inspector, subpackageClass2);
        },
        ImmutableList.of(
            "AssertionError in testclasses.Class1",
            "AssertionError in testclasses.subpackage.Class2",
            "DONE"));
    runR8Test(
        builder ->
            builder
                .addAssertionsConfiguration(
                    b -> b.setCompileTimeEnable().setScopePackage(packageName).build())
                .addAssertionsConfiguration(
                    b -> b.setCompileTimeDisable().setScopeClass(class2.getCanonicalName()).build())
                .addAssertionsConfiguration(
                    b ->
                        b.setCompileTimeDisable()
                            .setScopeClass(subpackageClass1.getCanonicalName())
                            .build()),
        inspector -> {
          checkAssertionCodeEnabled(inspector, class1);
          checkAssertionCodeRemoved(inspector, class2);
          checkAssertionCodeRemoved(inspector, subpackageClass1);
          checkAssertionCodeEnabled(inspector, subpackageClass2);
        },
        ImmutableList.of(
            "AssertionError in testclasses.Class1",
            "AssertionError in testclasses.subpackage.Class2",
            "DONE"));
  }

  @Test
  public void testUnnamedPackageForDex() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramClasses(class1, class2)
        .addProgramClassFileData(
            testClassForUnknownPackage(),
            classInUnnamedPackage("Class1"),
            classInUnnamedPackage("Class2"))
        .setMinApi(parameters.getApiLevel())
        .addAssertionsConfiguration(
            builder -> builder.setCompileTimeEnable().setScopePackage("").build())
        .compile()
        .inspect(
            inspector -> {
              AssertionsCheckerUtils.checkAssertionCodeEnabled(inspector.clazz("Class1"), "m");
              AssertionsCheckerUtils.checkAssertionCodeEnabled(inspector.clazz("Class2"), "m");
              checkAssertionCodeRemoved(inspector.clazz(class1));
              checkAssertionCodeRemoved(inspector.clazz(class2));
            })
        .run(parameters.getRuntime(), "Main")
        .assertSuccessWithOutputLines(
            "AssertionError in Class1", "AssertionError in Class2", "DONE");
  }

  @Test
  public void testInnerClassForJvm() throws Exception {
    Assume.assumeTrue(parameters.isCfRuntime());
    // Pointing to the outer class enables assertions for the inner as well.
    testForJvm()
        .addProgramClasses(TestClassForInnerClass.class, TestClassForInnerClass.InnerClass.class)
        .addVmArguments("-ea:" + TestClassForInnerClass.class.getCanonicalName())
        .run(parameters.getRuntime(), TestClassForInnerClass.class)
        .assertSuccessWithOutputLines(
            "AssertionError in TestClassForInnerClass",
            "AssertionError in TestClassForInnerClass.InnerClass",
            "DONE");

    // Pointing to the inner class enables no assertions.
    testForJvm()
        .addProgramClasses(TestClassForInnerClass.class, TestClassForInnerClass.InnerClass.class)
        .addVmArguments("-ea:" + TestClassForInnerClass.InnerClass.class.getCanonicalName())
        .run(parameters.getRuntime(), TestClassForInnerClass.class)
        .assertSuccessWithOutputLines("DONE");
    testForJvm()
        .addProgramClasses(TestClassForInnerClass.class, TestClassForInnerClass.InnerClass.class)
        .addVmArguments("-ea:" + TestClassForInnerClass.InnerClass.class.getTypeName())
        .run(parameters.getRuntime(), TestClassForInnerClass.class)
        .assertSuccessWithOutputLines("DONE");
  }

  @Test
  public void testInnerClassForDex() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramClasses(TestClassForInnerClass.class, TestClassForInnerClass.InnerClass.class)
        .setMinApi(parameters.getApiLevel())
        .addAssertionsConfiguration(
            builder ->
                builder
                    .setCompileTimeEnable()
                    .setScopeClass(TestClassForInnerClass.class.getCanonicalName())
                    .build())
        .compile()
        .inspect(
            inspector -> {
              AssertionsCheckerUtils.checkAssertionCodeEnabled(
                  inspector.clazz(TestClassForInnerClass.class), "m");
              AssertionsCheckerUtils.checkAssertionCodeEnabled(
                  inspector.clazz(TestClassForInnerClass.InnerClass.class), "m");
            })
        .run(parameters.getRuntime(), TestClassForInnerClass.class)
        .assertSuccessWithOutputLines(
            "AssertionError in TestClassForInnerClass",
            "AssertionError in TestClassForInnerClass.InnerClass",
            "DONE");
  }

  /**
   * Code for the following class in the unnamed package:
   *
   * <pre>
   *   public class Main {
   *     public static void main(String[] args) {
   *       try {
   *         Class1.m();
   *       } catch (AssertionError e) {
   *         System.out.println("AssertionError in Class1");
   *       } try {
   *         Class2.m();
   *       } catch (AssertionError e) {
   *         System.out.println("AssertionError in Class2");
   *       } try {
   *         com.android.tools.r8.rewrite.assertions.Class1.m();
   *       } catch (AssertionError e) {
   *         System.out.println("AssertionError in Class1");
   *       } try {
   *         com.android.tools.r8.rewrite.assertions.Class2.m();
   *       } catch (AssertionError e) {
   *         System.out.println("AssertionError in Class2");
   *       } System.out.println("DONE");
   *     }
   *   }
   * </pre>
   */
  public static byte[] testClassForUnknownPackage() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(V1_8, ACC_FINAL | ACC_SUPER, "Main", null, "java/lang/Object", null);

    classWriter.visitSource("Main.java", null);

    {
      methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(1, label0);
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
      Label label0 = new Label();
      Label label1 = new Label();
      Label label2 = new Label();
      methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/AssertionError");
      Label label3 = new Label();
      Label label4 = new Label();
      Label label5 = new Label();
      methodVisitor.visitTryCatchBlock(label3, label4, label5, "java/lang/AssertionError");
      Label label6 = new Label();
      Label label7 = new Label();
      Label label8 = new Label();
      methodVisitor.visitTryCatchBlock(label6, label7, label8, "java/lang/AssertionError");
      Label label9 = new Label();
      Label label10 = new Label();
      Label label11 = new Label();
      methodVisitor.visitTryCatchBlock(label9, label10, label11, "java/lang/AssertionError");
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(4, label0);
      methodVisitor.visitMethodInsn(INVOKESTATIC, "Class1", "m", "()V", false);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(7, label1);
      methodVisitor.visitJumpInsn(GOTO, label3);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(5, label2);
      methodVisitor.visitFrame(
          Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/AssertionError"});
      methodVisitor.visitVarInsn(ASTORE, 1);
      Label label12 = new Label();
      methodVisitor.visitLabel(label12);
      methodVisitor.visitLineNumber(6, label12);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("AssertionError in Class1");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(9, label3);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitMethodInsn(INVOKESTATIC, "Class2", "m", "()V", false);
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(12, label4);
      methodVisitor.visitJumpInsn(GOTO, label6);
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(10, label5);
      methodVisitor.visitFrame(
          Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/AssertionError"});
      methodVisitor.visitVarInsn(ASTORE, 1);
      Label label13 = new Label();
      methodVisitor.visitLabel(label13);
      methodVisitor.visitLineNumber(11, label13);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("AssertionError in Class2");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(14, label6);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/rewrite/assertions/testclasses/Class1",
          "m",
          "()V",
          false);
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(17, label7);
      methodVisitor.visitJumpInsn(GOTO, label9);
      methodVisitor.visitLabel(label8);
      methodVisitor.visitLineNumber(15, label8);
      methodVisitor.visitFrame(
          Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/AssertionError"});
      methodVisitor.visitVarInsn(ASTORE, 1);
      Label label14 = new Label();
      methodVisitor.visitLabel(label14);
      methodVisitor.visitLineNumber(16, label14);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("AssertionError in testclasses.Class1");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label9);
      methodVisitor.visitLineNumber(19, label9);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/rewrite/assertions/testclasses/Class2",
          "m",
          "()V",
          false);
      methodVisitor.visitLabel(label10);
      methodVisitor.visitLineNumber(22, label10);
      Label label15 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label15);
      methodVisitor.visitLabel(label11);
      methodVisitor.visitLineNumber(20, label11);
      methodVisitor.visitFrame(
          Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/AssertionError"});
      methodVisitor.visitVarInsn(ASTORE, 1);
      Label label16 = new Label();
      methodVisitor.visitLabel(label16);
      methodVisitor.visitLineNumber(21, label16);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("AssertionError in testclasses.Class2");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitLabel(label15);
      methodVisitor.visitLineNumber(24, label15);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("DONE");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label17 = new Label();
      methodVisitor.visitLabel(label17);
      methodVisitor.visitLineNumber(25, label17);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  /**
   * Code for the following class in the unnamed package:
   *
   * <pre>
   *   public class <name> {
   *     public static void m() {
   *       assert false;
   *     }
   *   }
   * </pre>
   */
  public static byte[] classInUnnamedPackage(String name) {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;

    classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, name, null, "java/lang/Object", null);

    classWriter.visitSource(name + ".java", null);

    {
      fieldVisitor =
          classWriter.visitField(
              ACC_FINAL | ACC_STATIC | ACC_SYNTHETIC, "$assertionsDisabled", "Z", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(1, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "m", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(3, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, name, "$assertionsDisabled", "Z");
      Label label1 = new Label();
      methodVisitor.visitJumpInsn(IFNE, label1);
      methodVisitor.visitTypeInsn(NEW, "java/lang/AssertionError");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/AssertionError", "<init>", "()V", false);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(4, label1);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(1, label0);
      methodVisitor.visitLdcInsn(Type.getType("L" + name + ";"));
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Class", "desiredAssertionStatus", "()Z", false);
      Label label1 = new Label();
      methodVisitor.visitJumpInsn(IFNE, label1);
      methodVisitor.visitInsn(ICONST_1);
      Label label2 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label2);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {Opcodes.INTEGER});
      methodVisitor.visitFieldInsn(PUTSTATIC, name, "$assertionsDisabled", "Z");
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  static class TestClass {
    public static void main(String[] args) {
      try {
        com.android.tools.r8.rewrite.assertions.testclasses.Class1.m();
      } catch (AssertionError e) {
        System.out.println("AssertionError in testclasses.Class1");
      }
      try {
        com.android.tools.r8.rewrite.assertions.testclasses.Class2.m();
      } catch (AssertionError e) {
        System.out.println("AssertionError in testclasses.Class2");
      }
      try {
        com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class1.m();
      } catch (AssertionError e) {
        System.out.println("AssertionError in testclasses.subpackage.Class1");
      }
      try {
        com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class2.m();
      } catch (AssertionError e) {
        System.out.println("AssertionError in testclasses.subpackage.Class2");
      }
      System.out.println("DONE");
    }
  }
}
