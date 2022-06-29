// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static org.junit.Assert.assertThrows;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.V1_8;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;

/** This is a regression test for b/237124748 */
@RunWith(Parameterized.class)
public class RepackageObjectOnProgramPathTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    assertThrows(
        CompilationFailedException.class,
        () -> {
          testForR8(parameters.getBackend())
              .addProgramClassFileData(dumpObject())
              .addProgramClasses(A.class, Main.class)
              .setMinApi(parameters.getApiLevel())
              .enableInliningAnnotations()
              .addKeepMainRule(Main.class)
              .addDontWarn("*")
              .compileWithExpectedDiagnostics(
                  diagnostics ->
                      // TODO(b/237124748): We should not throw an error.
                      diagnostics.assertErrorThatMatches(
                          DiagnosticsMatcher.diagnosticException(NullPointerException.class)));
        });
  }

  public static byte[] dumpObject() {
    ClassWriter classWriter = new ClassWriter(0);
    classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, "java/lang/Object", null, null, null);
    classWriter.visitEnd();
    return classWriter.toByteArray();
  }

  public static class A {

    @NeverInline
    public static void foo() {
      System.out.println("A::foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A.foo();
    }
  }
}
