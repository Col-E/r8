// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.enums;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.MethodTransformer;
import java.io.IOException;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class EnumInvalidValuesLengthTest extends TestBase {
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public EnumInvalidValuesLengthTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testValuesLength() throws Exception {
    Assume.assumeTrue(
        "TODO(b/172903562): Breaks on dex due to enum unboxing", parameters.isCfRuntime());
    testForR8(parameters.getBackend())
        .addKeepMainRule(EnumInvalidValuesLengthTest.Main.class)
        .addProgramClasses(EnumInvalidValuesLengthTest.Main.class)
        .addProgramClassFileData(transformValues(MyEnum.class))
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), EnumInvalidValuesLengthTest.Main.class)
        .assertSuccessWithOutputLines("5");
  }

  private byte[] transformValues(Class<MyEnum> myEnumClass) throws IOException {
    return transformer(myEnumClass)
        .addMethodTransformer(
            new MethodTransformer() {
              @Override
              public void visitInsn(int opcode) {
                if (opcode == Opcodes.ICONST_3) {
                  // This is the constant determining the size of the values array.
                  super.visitInsn(Opcodes.ICONST_5);
                  return;
                }
                super.visitInsn(opcode);
              }
            })
        .transform();
  }

  @NeverClassInline
  enum MyEnum {
    A,
    B,
    C;
  }

  public static class Main {
    public static void main(String[] args) {
      System.out.println(MyEnum.values().length);
    }
  }
}
