// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.nestaccesscontrol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.TypeSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class NestAttributesInDexShrinkingFieldsTest extends NestAttributesInDexTestBase
    implements Opcodes {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!");

  @Test
  public void testRuntime() throws Exception {
    assumeTrue(
        parameters.isCfRuntime()
            && isRuntimeWithNestSupport(parameters.asCfRuntime())
            && parameters.getApiLevel().isEqualTo(AndroidApiLevel.B));
    testForJvm(parameters)
        .addProgramClassFileData(
            dumpHost(ACC_PRIVATE), dumpMember1(ACC_PRIVATE), dumpMember2(ACC_PRIVATE))
        .run(parameters.getRuntime(), "Host")
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector, boolean expectNestAttributes) {
    ClassSubject host = inspector.clazz("Host");
    ClassSubject member1 = inspector.clazz("Host$Member1");
    ClassSubject member2 = inspector.clazz("Host$Member2");
    if (expectNestAttributes) {
      assertEquals(
          ImmutableList.of(member2.asTypeSubject(), member1.asTypeSubject()),
          host.getFinalNestMembersAttribute());
    } else {
      assertEquals(0, host.getFinalNestMembersAttribute().size());
    }
    TypeSubject expectedNestHostAttribute = expectNestAttributes ? host.asTypeSubject() : null;
    assertEquals(expectedNestHostAttribute, member1.getFinalNestHostAttribute());
    assertEquals(expectedNestHostAttribute, member2.getFinalNestHostAttribute());
  }

  private void expectNestAttributes(CodeInspector inspector) {
    inspect(inspector, true);
  }

  private void expectNoNestAttributes(CodeInspector inspector) {
    inspect(inspector, false);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(parameters.isDexRuntime() || isRuntimeWithNestSupport(parameters.asCfRuntime()));
    // TODO(b/247047415): Update test when a DEX VM natively supporting nests is added.
    assertFalse(parameters.getApiLevel().getLevel() > 34);
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            dumpHost(ACC_PRIVATE), dumpMember1(ACC_PRIVATE), dumpMember2(ACC_PRIVATE))
        .addKeepMainRule("Host")
        .setMinApi(parameters)
        .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
        .compile()
        .inspect(inspector -> assertEquals(1, inspector.allClasses().size()))
        .run(parameters.getRuntime(), "Host")
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8KeepHostWithPrivateMembers() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(parameters.isDexRuntime() || isRuntimeWithNestSupport(parameters.asCfRuntime()));
    // TODO(b/247047415): Update test when a DEX VM natively supporting nests is added.
    assertFalse(parameters.getApiLevel().getLevel() > 34);
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            dumpHost(ACC_PRIVATE), dumpMember1(ACC_PRIVATE), dumpMember2(ACC_PRIVATE))
        .setMinApi(parameters)
        .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
        .addKeepMainRule("Host")
        .addKeepClassAndMembersRules("Host", "Host$Member1", "Host$Member2")
        .compile()
        .inspect(this::expectNestAttributes)
        .run(parameters.getRuntime(), "Host")
        .applyIf(
            parameters.isCfRuntime(),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT),
            r -> r.assertFailureWithErrorThatThrows(IllegalAccessError.class));
  }

  @Test
  public void testR8KeepHostWithPublicMembers() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(parameters.isDexRuntime() || isRuntimeWithNestSupport(parameters.asCfRuntime()));
    // TODO(b/247047415): Update test when a DEX VM natively supporting nests is added.
    assertFalse(parameters.getApiLevel().getLevel() > 34);
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            dumpHost(ACC_PUBLIC), dumpMember1(ACC_PUBLIC), dumpMember2(ACC_PUBLIC))
        .setMinApi(parameters)
        .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
        .addKeepMainRule("Host")
        .addKeepClassAndMembersRules("Host", "Host$Member1", "Host$Member2")
        .compile()
        .inspect(this::expectNoNestAttributes)
        .run(parameters.getRuntime(), "Host")
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  /*
    Dump of:

    public class Host {
      private String s1 = ", ";
      private String s2 = "!";
      public static void main(String[] args) {
        new Host().h1();
        System.out.println();
      }

      static class Member1 {
        private String s = "Hello";
      }

      static class Member2 {
        private String s = "world";

        public void m(Host host) {
          System.out.println(host.s1);
        }
      }

      public void h1() {
        System.out.print(new Member1().s);
        System.out.print(s1);
        System.out.print(new Member2().s);
        new Member2().m(this);
      }
    }

    compiled with `-target 11`. Not a transformer here, as transforming the javac post nest
    access methods is not feasible.
  */

  public static byte[] dumpHost(int fieldAccess) {
    assert fieldAccess == ACC_PUBLIC || fieldAccess == ACC_PRIVATE;

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;

    classWriter.visit(V11, ACC_PUBLIC | ACC_SUPER, "Host", null, "java/lang/Object", null);

    classWriter.visitSource("Host.java", null);

    classWriter.visitNestMember("Host$Member2");

    classWriter.visitNestMember("Host$Member1");

    classWriter.visitInnerClass("Host$Member2", "Host", "Member2", ACC_STATIC);

    classWriter.visitInnerClass("Host$Member1", "Host", "Member1", ACC_STATIC);

    {
      fieldVisitor = classWriter.visitField(fieldAccess, "s1", "Ljava/lang/String;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor = classWriter.visitField(fieldAccess, "s2", "Ljava/lang/String;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(2, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(3, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitLdcInsn(", ");
      methodVisitor.visitFieldInsn(PUTFIELD, "Host", "s1", "Ljava/lang/String;");
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(4, label2);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitLdcInsn("!");
      methodVisitor.visitFieldInsn(PUTFIELD, "Host", "s2", "Ljava/lang/String;");
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(5, label0);
      methodVisitor.visitTypeInsn(NEW, "Host");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "Host", "<init>", "()V", false);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "Host", "h1", "()V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(6, label1);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "()V", false);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(7, label2);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "h1", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(26, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitTypeInsn(NEW, "Host$Member1");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "Host$Member1", "<init>", "()V", false);
      methodVisitor.visitFieldInsn(GETFIELD, "Host$Member1", "s", "Ljava/lang/String;");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(27, label1);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(GETFIELD, "Host", "s1", "Ljava/lang/String;");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(28, label2);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitTypeInsn(NEW, "Host$Member2");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "Host$Member2", "<init>", "()V", false);
      methodVisitor.visitFieldInsn(GETFIELD, "Host$Member2", "s", "Ljava/lang/String;");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(30, label3);
      methodVisitor.visitTypeInsn(NEW, "Host$Member2");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "Host$Member2", "<init>", "()V", false);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "Host$Member2", "m", "(LHost;)V", false);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(31, label4);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(3, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  public static byte[] dumpMember1(int fieldAccess) {
    assert fieldAccess == ACC_PUBLIC || fieldAccess == ACC_PRIVATE;

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;

    classWriter.visit(V11, ACC_SUPER, "Host$Member1", null, "java/lang/Object", null);

    classWriter.visitSource("Host.java", null);

    classWriter.visitNestHost("Host");

    classWriter.visitInnerClass("Host$Member1", "Host", "Member1", ACC_STATIC);

    {
      fieldVisitor = classWriter.visitField(fieldAccess, "s", "Ljava/lang/String;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(9, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(10, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitLdcInsn("Hello");
      methodVisitor.visitFieldInsn(PUTFIELD, "Host$Member1", "s", "Ljava/lang/String;");
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  public static byte[] dumpMember2(int fieldAccess) {
    assert fieldAccess == ACC_PUBLIC || fieldAccess == ACC_PRIVATE;

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;

    classWriter.visit(V11, ACC_SUPER, "Host$Member2", null, "java/lang/Object", null);

    classWriter.visitSource("Host.java", null);

    classWriter.visitNestHost("Host");

    classWriter.visitInnerClass("Host$Member2", "Host", "Member2", ACC_STATIC);

    {
      fieldVisitor = classWriter.visitField(fieldAccess, "s", "Ljava/lang/String;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(16, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(17, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitLdcInsn("world");
      methodVisitor.visitFieldInsn(PUTFIELD, "Host$Member2", "s", "Ljava/lang/String;");
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "m", "(LHost;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(20, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitFieldInsn(GETFIELD, "Host", "s2", "Ljava/lang/String;");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(21, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
