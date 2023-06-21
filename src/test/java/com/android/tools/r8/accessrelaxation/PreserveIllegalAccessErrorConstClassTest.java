// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Type;

@RunWith(Parameterized.class)
public class PreserveIllegalAccessErrorConstClassTest extends TestBase {

  private static final String NEW_A_DESCRIPTOR = "LA;";

  private static List<byte[]> programClassFileData;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @BeforeClass
  public static void setup() throws Exception {
    programClassFileData =
        ImmutableList.of(
            transformer(Main.class)
                .transformLdcInsnInMethod(
                    "main",
                    (object, visitor) -> {
                      if (object instanceof Type) {
                        Type type = (Type) object;
                        assertEquals(descriptor(A.class), type.getDescriptor());
                        visitor.visitLdcInsn(Type.getType(NEW_A_DESCRIPTOR));
                      } else {
                        visitor.visitLdcInsn(object);
                      }
                    })
                .transform(),
            transformer(A.class).setClassDescriptor(NEW_A_DESCRIPTOR).transform());
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(programClassFileData)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(programClassFileData)
        .addKeepMainRule(Main.class)
        .allowAccessModification()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  static class Main {

    public static void main(String[] args) {
      Class<?> clazz = A.class;
    }
  }

  static class /*different_package.*/ A {}
}
