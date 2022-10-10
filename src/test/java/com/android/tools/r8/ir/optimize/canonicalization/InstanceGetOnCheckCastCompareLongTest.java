// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.canonicalization;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

/** This is a regression test for b/251015885. */
@RunWith(Parameterized.class)
public class InstanceGetOnCheckCastCompareLongTest extends TestBase {

  private final String EXPECTED = "Hello World!";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(I.class, Main.class)
        .addProgramClassFileData(getTestClassWithRewrittenLongCompareToLCmp())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    // For R8 we will try to shorten the live range and we have a bailout to find an insertion
    // point if we see a cmp instruction and the api is lower than 23. The receiver of the instance
    // get is not defined on the insertion point which is why this regression test was made.
    R8FullTestBuilder r8FullTestBuilder =
        testForR8(parameters.getBackend())
            .addProgramClasses(I.class, Main.class)
            .addProgramClassFileData(getTestClassWithRewrittenLongCompareToLCmp())
            .addKeepMainRule(Main.class)
            .addKeepClassRules(I.class)
            .setMinApi(parameters.getApiLevel())
            .enableInliningAnnotations();
    if (parameters.getApiLevel().isLessThan(AndroidApiLevel.M)) {
      // TODO(b/251015885). We should not fail compilation.
      assertThrows(
          CompilationFailedException.class,
          () ->
              r8FullTestBuilder.compileWithExpectedDiagnostics(
                  diagnostics ->
                      diagnostics.assertErrorMessageThatMatches(
                          containsString("Unexpected values live at entry to first block"))));

    } else {
      r8FullTestBuilder
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutputLines(EXPECTED);
    }
  }

  private byte[] getTestClassWithRewrittenLongCompareToLCmp() throws Exception {
    return transformer(TestClass.class)
        .transformMethodInsnInMethod(
            "test",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (name.equals("compare")) {
                visitor.visitInsn(Opcodes.LCMP);
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  interface I {

    boolean isTrue();
  }

  public static class TestClass {
    @NeverInline
    public static boolean test(Long longValue, I object) {
      if (object != null) {
        long longVal = longValue;
        // Compiling with JDK8 produces LCMP where JDK 11 keeps the method call. We need LCMP.
        int compare = Long.compare(longVal, 0L); // <-- will be LCMP after transforming.
        Main main = (Main) object;
        boolean otherVal = main.field;
        if (compare != 0) {
          return otherVal;
        } else {
          return !otherVal;
        }
      }
      return false;
    }
  }

  static class Main implements I {

    final boolean field = System.currentTimeMillis() > 0;

    public static void main(String[] args) {
      if (TestClass.test(System.currentTimeMillis(), new Main())) {
        System.out.println("Hello World!");
      }
    }

    @Override
    public boolean isTrue() {
      return field;
    }
  }
}
