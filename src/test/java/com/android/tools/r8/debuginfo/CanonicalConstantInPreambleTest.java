// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class CanonicalConstantInPreambleTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public CanonicalConstantInPreambleTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(getTransformedTestClass())
        .apply(
            b -> {
              // Disable shorten live ranges to ensure the canonicalized constant remains in
              // preamble.
              if (b instanceof D8TestBuilder) {
                ((D8TestBuilder) b)
                    .addOptionsModification(o -> o.testing.disableShortenLiveRanges = true);
              }
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private byte[] getTransformedTestClass() throws Exception {
    return transformer(TestClass.class)
        .stripFrames("foo")
        .setVersion(CfVersion.V1_5)
        .removeLineNumberTable(MethodPredicate.onName("foo"))
        .transformMethodInsnInMethod(
            "foo",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (!name.equals("nanoTime")) {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
              }
              // Jump over the preamble code.
              Label postPreamble = new Label();
              visitor.visitJumpInsn(Opcodes.GOTO, postPreamble);
              visitor.visitLabel(postPreamble);
              visitor.visitInsn(Opcodes.LCONST_0);
              // Start an actual line after the preamble instructions.
              Label firstLine = new Label();
              visitor.visitLabel(firstLine);
              visitor.visitLineNumber(42, firstLine);
              // Create a trivial block that just has the active line and falls through.
              // This is the block that we expect to have been removed, but due to the constant
              // being moved up and having the wrong line it will fail.
              Label trivialFallthrough = new Label();
              visitor.visitJumpInsn(Opcodes.GOTO, trivialFallthrough);
              visitor.visitLabel(trivialFallthrough);
            })
        .transform();
  }

  static class TestClass {

    public static void foo(String arg) {
      System.nanoTime(); // Will be replaced by transformed to preamble code.
      // The constant '1' will be canonicalized and inserted in the preamble.
      if (arg.equals("0") || arg.equals("1")) {
        System.out.print("Hello, ");
      }
      if (!arg.equals("1")) {
        System.out.println("world");
      }
    }

    public static void main(String[] args) {
      foo(String.valueOf(args.length));
    }
  }
}
