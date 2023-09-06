// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

@RunWith(Parameterized.class)
public class LambdasInHugeMethod extends TestBase implements Opcodes {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  // With 1311 the TestClass main method exceeds class file method size limit
  static int repeat = 1310;

  private static final String EXPECTED_OUTPUT;

  static {
    List<String> lines = new ArrayList<>();
    for (int i = 0; i < repeat; i++) {
      lines.add("1");
      lines.add("2");
      lines.add("3");
      lines.add("4.0");
      lines.add("5");
    }
    EXPECTED_OUTPUT = StringUtils.lines(lines);
  }

  @Test
  public void testRuntime() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(MyConsumer.class, MyTriConsumer.class)
        .addProgramClassFileData(generateTestClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testDesugaring() throws Exception {
    // Cf to cf desugaring fails generating too large method in class file.
    if (parameters.isCfRuntime()) {
      assertThrows(
          CompilationFailedException.class,
          () ->
              testForDesugaring(
                      parameters,
                      (options) -> {
                        /* no options change */
                      },
                      configuration -> configuration == DesugarTestConfiguration.D8_CF)
                  .addProgramClasses(MyConsumer.class, MyTriConsumer.class)
                  .addProgramClassFileData(generateTestClass())
                  .run(parameters.getRuntime(), TestClass.class));
    } else {
      assertThrows(
          CompilationFailedException.class,
          () ->
              testForDesugaring(
                      parameters,
                      (options) -> {
                        /* no options change */
                      },
                      configuration -> configuration == DesugarTestConfiguration.D8_CF_D8_DEX)
                  .addProgramClasses(MyConsumer.class, MyTriConsumer.class)
                  .addProgramClassFileData(generateTestClass())
                  .run(parameters.getRuntime(), TestClass.class));
    }

    // Desugaring directly to DEX works with large methods.
    testForDesugaring(
            parameters,
            (options) -> {
              /* no options change */
            },
            configuration ->
                configuration != DesugarTestConfiguration.D8_CF
                    && configuration != DesugarTestConfiguration.D8_CF_D8_DEX)
        .addProgramClasses(MyConsumer.class, MyTriConsumer.class)
        .addProgramClassFileData(generateTestClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(MyConsumer.class, MyTriConsumer.class)
        .addProgramClassFileData(generateTestClass())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private byte[] generateTestClass() throws Exception {
    return transformer(TestClass.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              // assert this is the placeholder.
              for (int i = 0; i < repeat; i++) {
                useStatelessLambda(visitor);
                useStatefulLambdaWithOneCapture(visitor);
                useStatefulLambdaWithThreeCaptures(visitor);
              }
            })
        .setMaxStackHeight(MethodPredicate.onName("main"), 4)
        .transform();
  }

  public interface MyConsumer<T> {
    void create(T o);
  }

  public interface MyTriConsumer<T, U, V> {
    void accept(T o1, U o2, V o3);
  }

  static class TestClass {

    public static void greet() {
      System.out.println("1");
    }

    public static void greet(MyConsumer<String> consumer) {
      consumer.create("2");
    }

    public static void greetTri(long l, double d, String s) {
      System.out.println(l);
      System.out.println(d);
      System.out.println(s);
    }

    public static void main(String[] args) throws Exception {
      // Single static invoke instruction transformed.
      greet();
      // **** This code block is repeated "repeat" times  in place of "greet()" ****:
      // ((Runnable) TestClass::greet).run();
      // greet(System.out::println);
      // ((MyTriConsumer<Long, Double, String>) TestClass::greetTri).accept(3L, 4.0, "5");
    }
  }

  private static void useStatelessLambda(MethodVisitor methodVisitor) {
    methodVisitor.visitInvokeDynamicInsn(
        "run",
        "()Ljava/lang/Runnable;",
        new Handle(
            Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            false),
        Type.getType("()V"),
        new Handle(
            Opcodes.H_INVOKESTATIC,
            "com/android/tools/r8/desugar/lambdas/LambdasInHugeMethod$TestClass",
            "greet",
            "()V",
            false),
        Type.getType("()V"));
    methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/lang/Runnable", "run", "()V", true);
  }

  private static void useStatefulLambdaWithOneCapture(MethodVisitor methodVisitor) {
    methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    methodVisitor.visitInsn(DUP);
    methodVisitor.visitMethodInsn(
        INVOKESTATIC,
        "java/util/Objects",
        "requireNonNull",
        "(Ljava/lang/Object;)Ljava/lang/Object;",
        false);
    methodVisitor.visitInsn(POP);
    methodVisitor.visitInvokeDynamicInsn(
        "create",
        "(Ljava/io/PrintStream;)Lcom/android/tools/r8/desugar/lambdas/LambdasInHugeMethod$MyConsumer;",
        new Handle(
            Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            false),
        new Object[] {
          Type.getType("(Ljava/lang/Object;)V"),
          new Handle(
              Opcodes.H_INVOKEVIRTUAL,
              "java/io/PrintStream",
              "println",
              "(Ljava/lang/String;)V",
              false),
          Type.getType("(Ljava/lang/String;)V")
        });
    methodVisitor.visitMethodInsn(
        INVOKESTATIC,
        "com/android/tools/r8/desugar/lambdas/LambdasInHugeMethod$TestClass",
        "greet",
        "(Lcom/android/tools/r8/desugar/lambdas/LambdasInHugeMethod$MyConsumer;)V",
        false);
    methodVisitor.visitInvokeDynamicInsn(
        "accept",
        "()Lcom/android/tools/r8/desugar/lambdas/LambdasInHugeMethod$MyTriConsumer;",
        new Handle(
            Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            false),
        new Object[] {
          Type.getType("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V"),
          new Handle(
              Opcodes.H_INVOKESTATIC,
              "com/android/tools/r8/desugar/lambdas/LambdasInHugeMethod$TestClass",
              "greetTri",
              "(JDLjava/lang/String;)V",
              false),
          Type.getType("(Ljava/lang/Long;Ljava/lang/Double;Ljava/lang/String;)V")
        });
  }

  private static void useStatefulLambdaWithThreeCaptures(MethodVisitor methodVisitor) {
    methodVisitor.visitLdcInsn(3L);
    methodVisitor.visitMethodInsn(
        INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
    methodVisitor.visitLdcInsn(Double.parseDouble("4.0"));
    methodVisitor.visitMethodInsn(
        INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
    methodVisitor.visitLdcInsn("5");
    methodVisitor.visitMethodInsn(
        INVOKEINTERFACE,
        "com/android/tools/r8/desugar/lambdas/LambdasInHugeMethod$MyTriConsumer",
        "accept",
        "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
        true);
  }
}
