// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MemberRebindingArrayTest extends TestBase {

  private final TestParameters parameters;
  private final String[] EXPECTED = new String[] {"hashCode", "equals"};

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MemberRebindingArrayTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(getMainWithRewrittenEqualsAndHashCode())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getMainWithRewrittenEqualsAndHashCode())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .addOptionsModification(
            options -> options.apiModelingOptions().disableApiCallerIdentification())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private byte[] getMainWithRewrittenEqualsAndHashCode() throws Exception {
    return transformer(Main.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (owner.equals(binaryName(Object.class))) {
                visitor.visitMethodInsn(
                    opcode, "[" + descriptor(String.class), name, descriptor, isInterface);
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(args.hashCode() != 0 ? "hashCode" : "error_hashCode");
      System.out.println(args.equals(args.clone()) ? "error_equals" : "equals");
    }
  }
}
