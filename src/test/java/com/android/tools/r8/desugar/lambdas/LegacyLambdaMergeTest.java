// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodInsnTransform;
import com.android.tools.r8.transformers.ClassFileTransformer.TypeInsnTransform;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public class LegacyLambdaMergeTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public LegacyLambdaMergeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(getTransformedMain())
        // Add the lambda twice (JVM just picks the first).
        .addProgramClassFileData(getTransformedLambda())
        .addProgramClassFileData(getTransformedLambda())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    // Merging legacy lambdas is only valid for DEX inputs, thus also not R8 applicable.
    assumeTrue(parameters.isDexRuntime());
    D8TestCompileResult lambda =
        testForD8().setMinApi(parameters).addProgramClassFileData(getTransformedLambda()).compile();
    testForD8()
        .setMinApi(parameters)
        .addProgramClassFileData(getTransformedMain())
        // Add the lambda twice.
        .addProgramFiles(lambda.writeToZip())
        .addProgramFiles(lambda.writeToZip())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private ClassReference LAMBDA =
      Reference.classFromDescriptor(
          Reference.classFromClass(WillBeLambda.class)
              .getDescriptor()
              .replace("WillBeLambda", "-$$Lambda$XYZ"));

  private byte[] getTransformedLambda() throws Exception {
    return transformer(WillBeLambda.class)
        .setClassDescriptor(LAMBDA.getDescriptor())
        .setAccessFlags(AccessFlags::setSynthetic)
        .transform();
  }

  private byte[] getTransformedMain() throws Exception {
    return transformer(TestClass.class)
        .transformMethodInsnInMethod(
            "main",
            new MethodInsnTransform() {
              @Override
              public void visitMethodInsn(
                  int opcode,
                  String owner,
                  String name,
                  String descriptor,
                  boolean isInterface,
                  MethodVisitor visitor) {
                visitor.visitMethodInsn(
                    opcode, LAMBDA.getBinaryName(), name, descriptor, isInterface);
              }
            })
        .transformTypeInsnInMethod(
            "main",
            new TypeInsnTransform() {
              @Override
              public void visitTypeInsn(int opcode, String type, MethodVisitor visitor) {
                visitor.visitTypeInsn(opcode, LAMBDA.getBinaryName());
              }
            })
        .transform();
  }

  static class WillBeLambda {
    public void foo() {
      System.out.println("Hello, world");
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      new WillBeLambda().foo();
    }
  }
}
