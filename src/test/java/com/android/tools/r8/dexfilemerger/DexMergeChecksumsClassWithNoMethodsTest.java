// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexfilemerger;

import static org.objectweb.asm.Opcodes.V1_8;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class DexMergeChecksumsClassWithNoMethodsTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DexMergeChecksumsClassWithNoMethodsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testChecksumForClassWithNoMethods() throws Exception {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(
        V1_8,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        this.getClass().getTypeName().replace('.', '/') + "$" + "A",
        null,
        "java/lang/Object",
        null);
    cw.visitEnd();

    Path dexArchiveA =
        testForD8()
            .setMinApi(parameters.getApiLevel())
            .addProgramClassFileData(cw.toByteArray())
            .setMode(CompilationMode.DEBUG)
            .setIncludeClassesChecksum(true)
            .compile().writeToZip();

    Path dexArchiveTestClass =
        testForD8()
            .setMinApi(parameters.getApiLevel())
            .addProgramClasses(TestClass.class)
            .setMode(CompilationMode.DEBUG)
            .setIncludeClassesChecksum(true)
            .compile()
            .writeToZip();

    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramFiles(dexArchiveA, dexArchiveTestClass)
        .setIncludeClassesChecksum(true)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(this.getClass().getSimpleName() + "$A");
  }

  class A {}

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(A.class.getSimpleName());
    }
  }
}
