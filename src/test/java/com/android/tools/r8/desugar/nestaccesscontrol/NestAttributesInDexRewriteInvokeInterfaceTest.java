// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
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
import com.android.tools.r8.ToolHelper.DexVm.Version;
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
public class NestAttributesInDexRewriteInvokeInterfaceTest extends TestBase implements Opcodes {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .build();
  }

  private static final List<String> EXPECTED_OUTPUT_LINES = ImmutableList.of("Hello, world!");

  @Test
  public void testRuntime() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(
            dumpHost(),
            dumpMember1(),
            dumpMember2(),
            dumpHostImpl(),
            dumpMember1Impl(),
            dumpMember2Impl())
        .run(parameters.getRuntime(), "HostImpl")
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
    assertFalse(parameters.getApiLevel().getLevel() > 33);
    testForD8()
        .addProgramClassFileData(
            dumpHost(),
            dumpMember1(),
            dumpMember2(),
            dumpHostImpl(),
            dumpMember1Impl(),
            dumpMember2Impl())
        .setMinApi(parameters)
        .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
        .compile()
        .inspect(
            inspector -> {
              assertEquals(
                  2,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host").uniqueMethodWithOriginalName("h1")));
              assertEquals(
                  1,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host$Member1").uniqueMethodWithOriginalName("m")));
              assertEquals(
                  1,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host$Member2").uniqueMethodWithOriginalName("m")));
            })
        .run(parameters.getRuntime(), "HostImpl")
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @Test
  public void testD8WithClasspathAndMerge() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    // TODO(sgjesse): Update test when a DEX VM natively supporting nests is added.
    assertFalse(parameters.getApiLevel().getLevel() > 33);

    Path host =
        testForD8()
            .addProgramClassFileData(dumpHost())
            .addClasspathClassFileData(dumpMember1(), dumpMember2())
            .setMinApi(parameters)
            .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
            .compile()
            .inspect(
                inspector -> {
                  assertEquals(
                      2,
                      nonConstructorInvokeDirectCount(
                          inspector.clazz("Host").uniqueMethodWithOriginalName("h1")));
                })
            .writeToZip();

    Path member1 =
        testForD8()
            .addProgramClassFileData(dumpMember1())
            .addClasspathClassFileData(dumpHost(), dumpMember2())
            .setMinApi(parameters)
            .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
            .compile()
            .inspect(
                inspector -> {
                  assertEquals(
                      1,
                      nonConstructorInvokeDirectCount(
                          inspector.clazz("Host$Member1").uniqueMethodWithOriginalName("m")));
                })
            .writeToZip();

    Path member2 =
        testForD8()
            .addProgramClassFileData(dumpMember2())
            .addClasspathClassFileData(dumpHost(), dumpMember1())
            .setMinApi(parameters)
            .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
            .compile()
            .inspect(
                inspector -> {
                  assertEquals(
                      1,
                      nonConstructorInvokeDirectCount(
                          inspector.clazz("Host$Member2").uniqueMethodWithOriginalName("m")));
                })
            .writeToZip();

    Path impls =
        testForD8()
            .addProgramClassFileData(dumpHostImpl(), dumpMember1Impl(), dumpMember2Impl())
            .addClasspathClassFileData(dumpHost(), dumpMember1(), dumpMember2Impl())
            .setMinApi(parameters)
            .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
            .compile()
            .writeToZip();

    testForD8()
        .addProgramFiles(host, member1, member2, impls)
        .addClasspathClassFileData(dumpMember2())
        .setMinApi(parameters)
        .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
        .compile()
        .inspect(
            inspector -> {
              assertEquals(
                  2,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host").uniqueMethodWithOriginalName("h1")));
              assertEquals(
                  1,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host$Member1").uniqueMethodWithOriginalName("m")));
              assertEquals(
                  1,
                  nonConstructorInvokeDirectCount(
                      inspector.clazz("Host$Member2").uniqueMethodWithOriginalName("m")));
            })
        .run(parameters.getRuntime(), "HostImpl")
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @Test
  public void testD8WithoutMembersOnClasspath() {
    assumeTrue(parameters.isDexRuntime());
    // TODO(sgjesse): Update test when a DEX VM natively supporting nests is added.
    assertFalse(parameters.getApiLevel().getLevel() > 33);

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
    // TODO(sgjesse): Update test when a DEX VM natively supporting nests is added.
    assertFalse(parameters.getApiLevel().getLevel() > 33);

    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8()
                .addProgramClassFileData(dumpMember1(), dumpMember2())
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

    interface Host {

      interface Member1 {
        private void m(Host host) {
          host.h2("Hello");
        }
      }

      interface Member2 {
        private void m(Host host) {
          host.h2(", world!");
        }
      }

      default void h1(Member1 m1, Member2 m2) {
        m1.m(this);
        m2.m(this);
      }

      private void h2(String message) {
        System.out.print(message);
      }
    }

    class HostImpl implements Host {
      public static void main(String[] args) {
        Host host = new HostImpl();
        Host.Member1 member1 = new Member1Impl();
        Host.Member2 member2 = new Member2Impl();
        host.h1(member1, member2);
        System.out.println();
      }
    }

    class Member1Impl implements Host.Member1 {}

    class Member2Impl implements Host.Member2 {}

  */

  public static byte[] dumpHost() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(V11, ACC_ABSTRACT | ACC_INTERFACE, "Host", null, "java/lang/Object", null);
    classWriter.visitSource("Host.java", null);
    classWriter.visitNestMember("Host$Member2");
    classWriter.visitNestMember("Host$Member1");
    classWriter.visitInnerClass(
        "Host$Member2", "Host", "Member2", ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_INTERFACE);
    classWriter.visitInnerClass(
        "Host$Member1", "Host", "Member1", ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_INTERFACE);

    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC, "h1", "(LHost$Member1;LHost$Member2;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(16, label0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKEINTERFACE, "Host$Member1", "m", "(LHost;)V", true);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(17, label1);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKEINTERFACE, "Host$Member2", "m", "(LHost;)V", true);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(18, label2);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 3);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PRIVATE, "h2", "(Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(21, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(22, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();
    return classWriter.toByteArray();
  }

  public static byte[] dumpMember1() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(
        V11,
        ACC_PUBLIC | ACC_ABSTRACT | ACC_INTERFACE,
        "Host$Member1",
        null,
        "java/lang/Object",
        null);
    classWriter.visitSource("Host.java", null);
    classWriter.visitNestHost("Host");
    classWriter.visitInnerClass(
        "Host$Member1", "Host", "Member1", ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_INTERFACE);
    {
      methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "m", "(LHost;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(5, label0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitLdcInsn("Hello");
      methodVisitor.visitMethodInsn(INVOKEINTERFACE, "Host", "h2", "(Ljava/lang/String;)V", true);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(6, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  public static byte[] dumpMember2() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(
        V11,
        ACC_PUBLIC | ACC_ABSTRACT | ACC_INTERFACE,
        "Host$Member2",
        null,
        "java/lang/Object",
        null);
    classWriter.visitSource("Host.java", null);
    classWriter.visitNestHost("Host");
    classWriter.visitInnerClass(
        "Host$Member2", "Host", "Member2", ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_INTERFACE);
    {
      methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "m", "(LHost;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(11, label0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitLdcInsn(", world!");
      methodVisitor.visitMethodInsn(INVOKEINTERFACE, "Host", "h2", "(Ljava/lang/String;)V", true);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(12, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  public static byte[] dumpHostImpl() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(V11, ACC_SUPER, "HostImpl", null, "java/lang/Object", new String[] {"Host"});
    classWriter.visitSource("HostImpl.java", null);
    classWriter.visitInnerClass(
        "Host$Member1", "Host", "Member1", ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_INTERFACE);
    classWriter.visitInnerClass(
        "Host$Member2", "Host", "Member2", ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_INTERFACE);
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
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(3, label0);
      methodVisitor.visitTypeInsn(NEW, "HostImpl");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "HostImpl", "<init>", "()V", false);
      methodVisitor.visitVarInsn(ASTORE, 1);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(4, label1);
      methodVisitor.visitTypeInsn(NEW, "Member1Impl");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "Member1Impl", "<init>", "()V", false);
      methodVisitor.visitVarInsn(ASTORE, 2);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(5, label2);
      methodVisitor.visitTypeInsn(NEW, "Member2Impl");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "Member2Impl", "<init>", "()V", false);
      methodVisitor.visitVarInsn(ASTORE, 3);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(6, label3);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "Host", "h1", "(LHost$Member1;LHost$Member2;)V", true);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(7, label4);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "()V", false);
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(8, label5);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(3, 4);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  public static byte[] dumpMember1Impl() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(
        V11, ACC_SUPER, "Member1Impl", null, "java/lang/Object", new String[] {"Host$Member1"});
    classWriter.visitSource("Member1Impl.java", null);
    classWriter.visitInnerClass(
        "Host$Member1", "Host", "Member1", ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_INTERFACE);
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
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  public static byte[] dumpMember2Impl() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(
        V11, ACC_SUPER, "Member2Impl", null, "java/lang/Object", new String[] {"Host$Member2"});
    classWriter.visitSource("Member2Impl.java", null);
    classWriter.visitInnerClass(
        "Host$Member2", "Host", "Member2", ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_INTERFACE);
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
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
