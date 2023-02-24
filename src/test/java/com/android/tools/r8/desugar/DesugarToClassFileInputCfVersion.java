// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import static org.objectweb.asm.Opcodes.V1_4;
import static org.objectweb.asm.Opcodes.V1_5;
import static org.objectweb.asm.Opcodes.V1_6;
import static org.objectweb.asm.Opcodes.V1_7;
import static org.objectweb.asm.Opcodes.V1_8;
import static org.objectweb.asm.Opcodes.V9;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugarToClassFileInputCfVersion extends TestBase {

  @Parameters(name = "{0}, input Cf version: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        ImmutableList.of(V1_4, V1_5, V1_6, V1_7, V1_8, V9));
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public int cfVersion;

  @Test
  public void test() throws Exception {
    // Use D8 to desugar with Java classfile output.
    D8TestCompileResult desugarCompileResult =
        testForD8(Backend.CF)
            .addProgramClassFileData(transformer(TestClass.class).setVersion(cfVersion).transform())
            .setMinApi(parameters)
            .compile();

    if (parameters.getRuntime().isCf()) {
      // Run on the JVM given that Cf version is supported. When we desugar we now target 1.7 (51).
      desugarCompileResult
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello, world!");
    } else {
      assert parameters.getRuntime().isDex();
      // Convert to DEX without desugaring.
      testForD8()
          .addProgramFiles(desugarCompileResult.writeToZip())
          .setMinApi(parameters)
          .disableDesugaring()
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello, world!");
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      if (System.currentTimeMillis() > 0) {
        System.out.println("Hello, world!");
      }
    }
  }
}
