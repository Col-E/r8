// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EnumWithNonDefaultForwardingConstructorTest extends TestBase {

  private final boolean enableEnumUnboxing;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{1}, enum unboxing: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public EnumWithNonDefaultForwardingConstructorTest(
      boolean enableEnumUnboxing, TestParameters parameters) {
    this.enableEnumUnboxing = enableEnumUnboxing;
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    assumeFalse(enableEnumUnboxing);
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .apply(this::addProgramClasses)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("42");
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::addProgramClasses)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(options -> options.enableEnumUnboxing = enableEnumUnboxing)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("42");
  }

  private void addProgramClasses(TestBuilder<?, ?> builder) throws Exception {
    builder
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(
            transformer(MyEnum.class)
                .addMethodTransformer(
                    new MethodTransformer() {
                      @Override
                      public void visitVarInsn(int opcode, int var) {
                        if (getContext().getReference().getMethodName().equals("<init>")) {
                          // Pass the value of the third argument (ignoring the receiver) to the
                          // parent constructor instead of the value of the second argument.
                          super.visitVarInsn(opcode, var == 2 ? 3 : var);
                          return;
                        }
                        super.visitVarInsn(opcode, var);
                      }
                    })
                .transform());
  }

  static class TestClass {

    public static void main(String[] args) {
      MyEnum instance = System.currentTimeMillis() > 0 ? MyEnum.A : MyEnum.B;
      System.out.println(instance.ordinal());
    }
  }

  enum MyEnum {
    A(42),
    B(43);

    MyEnum(int ordinal) {}
  }
}
