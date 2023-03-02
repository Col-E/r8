// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class NestAttributesInDexRewriteInvokeSuperTest extends TestBase implements Opcodes {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private static final List<String> EXPECTED_OUTPUT_LINES = ImmutableList.of("Hello, world!");

  @Test
  public void testRuntime() throws Exception {
    assumeTrue(
        parameters.isCfRuntime()
            && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK11)
            && parameters.getApiLevel().isEqualTo(AndroidApiLevel.B));
    testForJvm(parameters)
        .addProgramClassFileData(dumpHost(), dumpMember(), dumpSubMember())
        .run(parameters.getRuntime(), "Host")
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT_LINES);
  }

  private int nonConstructorInvokeDirectCount(MethodSubject method) {
    return (int)
        method
            .streamInstructions()
            .filter(InstructionSubject::isInvokeSpecialOrDirect)
            .filter(instruction -> !instruction.getMethod().getName().toString().equals("<init>"))
            .count();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    // TODO(b/247047415): Update test when a DEX VM natively supporting nests is added.
    assertFalse(parameters.getApiLevel().getLevel() > 34);
    testForD8()
        .addProgramClassFileData(dumpHost(), dumpMember(), dumpSubMember())
        .setMinApi(parameters)
        .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
        .compile()
        .inspect(
            inspector -> {
              assertEquals(
                  1,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host").uniqueMethodWithOriginalName("main")));
              assertEquals(
                  1,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host").uniqueMethodWithOriginalName("h")));
              assertEquals(
                  0,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host$Member").uniqueMethodWithOriginalName("m")));
              assertEquals(
                  1,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host$SubMember").uniqueMethodWithOriginalName("m")));
            })
        .run(parameters.getRuntime(), "Host")
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @Test
  public void testD8WithClasspathAndMerge() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    // TODO(b/247047415): Update test when a DEX VM natively supporting nests is added.
    assertFalse(parameters.getApiLevel().getLevel() > 34);

    Path host =
        testForD8()
            .addProgramClassFileData(dumpHost())
            .addClasspathClassFileData(dumpMember(), dumpSubMember())
            .setMinApi(parameters)
            .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
            .compile()
            .inspect(
                inspector -> {
                  assertEquals(
                      1,
                      nonConstructorInvokeDirectCount(
                          inspector.clazz("Host").uniqueMethodWithOriginalName("main")));
                  assertEquals(
                      1,
                      nonConstructorInvokeDirectCount(
                          inspector.clazz("Host").uniqueMethodWithOriginalName("h")));
                })
            .writeToZip();

    Path member =
        testForD8()
            .addProgramClassFileData(dumpMember())
            .addClasspathClassFileData(dumpHost(), dumpSubMember())
            .setMinApi(parameters)
            .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
            .compile()
            .inspect(
                inspector -> {
                  assertEquals(
                      0,
                      nonConstructorInvokeDirectCount(
                          inspector.clazz("Host$Member").uniqueMethodWithOriginalName("m")));
                })
            .writeToZip();

    Path subMember =
        testForD8()
            .addProgramClassFileData(dumpSubMember())
            .addClasspathClassFileData(dumpHost(), dumpMember())
            .setMinApi(parameters)
            .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
            .compile()
            .inspect(
                inspector -> {
                  assertEquals(
                      1,
                      nonConstructorInvokeDirectCount(
                          inspector.clazz("Host$SubMember").uniqueMethodWithOriginalName("m")));
                })
            .writeToZip();

    testForD8()
        .addProgramFiles(host, member, subMember)
        .addClasspathClassFileData(dumpSubMember())
        .setMinApi(parameters)
        .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
        .compile()
        .inspect(
            inspector -> {
              assertEquals(
                  1,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host").uniqueMethodWithOriginalName("main")));
              assertEquals(
                  1,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host").uniqueMethodWithOriginalName("h")));
              assertEquals(
                  0,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host$Member").uniqueMethodWithOriginalName("m")));
              assertEquals(
                  1,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host$SubMember").uniqueMethodWithOriginalName("m")));
            })
        .run(parameters.getRuntime(), "Host")
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @Test
  public void testD8WithoutMembersOnClasspath() {
    assumeTrue(parameters.isDexRuntime());
    // TODO(b/247047415): Update test when a DEX VM natively supporting nests is added.
    assertFalse(parameters.getApiLevel().getLevel() > 34);

    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8()
                .addProgramClassFileData(dumpHost())
                .setMinApi(parameters)
                .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
                .compileWithExpectedDiagnostics(
                    diagnostics -> {
                      diagnostics.assertOnlyErrors();
                      diagnostics.assertErrorThatMatches(
                          (diagnosticMessage(containsString("Host requires its nest mates"))));
                    }));
  }

  @Test
  public void testD8WithoutHostOnClasspath() {
    assumeTrue(parameters.isDexRuntime());
    // TODO(b/247047415): Update test when a DEX VM natively supporting nests is added.
    assertFalse(parameters.getApiLevel().getLevel() > 34);

    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8()
                .addProgramClassFileData(dumpMember(), dumpSubMember())
                .setMinApi(parameters)
                .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
                .compileWithExpectedDiagnostics(
                    diagnostics -> {
                      diagnostics.assertOnlyErrors();
                      diagnostics.assertErrorThatMatches(
                          (diagnosticMessage(containsString("requires its nest host Host"))));
                    }));
  }

  /*
    Dump of:

    public class Host {
      public static void main(String[] args) {
        new Host().h();
        System.out.println();
      }

      private static class Member {
        private void m() {
          System.out.print("Hello, ");
        }
      }

      private static class SubMember extends Member {
        private void m(Host host) {
          super.m();
          System.out.print("world!");
        }
      }

      private void h() {
        new SubMember().m(this);
      }
    }
  */

  public static byte[] dumpHost() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(V11, ACC_PUBLIC | ACC_SUPER, "Host", null, "java/lang/Object", null);

    classWriter.visitSource("Host.java", null);

    classWriter.visitNestMember("Host$SubMember");

    classWriter.visitNestMember("Host$Member");

    classWriter.visitInnerClass("Host$SubMember", "Host", "SubMember", ACC_PRIVATE | ACC_STATIC);

    classWriter.visitInnerClass("Host$Member", "Host", "Member", ACC_PRIVATE | ACC_STATIC);

    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(2, label0);
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
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(4, label0);
      methodVisitor.visitTypeInsn(NEW, "Host");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "Host", "<init>", "()V", false);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "Host", "h", "()V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(5, label1);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "()V", false);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(6, label2);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "h", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(22, label0);
      methodVisitor.visitTypeInsn(NEW, "Host$SubMember");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "Host$SubMember", "<init>", "()V", false);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "Host$SubMember", "m", "(LHost;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(23, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  public static byte[] dumpMember() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(V11, ACC_SUPER, "Host$Member", null, "java/lang/Object", null);

    classWriter.visitSource("Host.java", null);

    classWriter.visitNestHost("Host");

    classWriter.visitInnerClass("Host$Member", "Host", "Member", ACC_PRIVATE | ACC_STATIC);

    {
      methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(8, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "m", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(10, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("Hello, ");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(11, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  public static byte[] dumpSubMember() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(V11, ACC_SUPER, "Host$SubMember", null, "Host$Member", null);

    classWriter.visitSource("Host.java", null);

    classWriter.visitNestHost("Host");

    classWriter.visitInnerClass("Host$SubMember", "Host", "SubMember", ACC_PRIVATE | ACC_STATIC);

    classWriter.visitInnerClass("Host$Member", "Host", "Member", ACC_PRIVATE | ACC_STATIC);

    {
      methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(14, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "Host$Member", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "m", "(LHost;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(16, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "Host$Member", "m", "()V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(17, label1);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("world!");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(18, label2);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
