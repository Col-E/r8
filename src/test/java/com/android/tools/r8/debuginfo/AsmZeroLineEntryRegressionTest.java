// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.IntBox;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Label;

@RunWith(Parameterized.class)
public class AsmZeroLineEntryRegressionTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultCfRuntime().build();
  }

  public AsmZeroLineEntryRegressionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  /**
   * Reference test showing JVM printing of zero-line valued entries in the line number table.
   *
   * <p>JVM spec defines line values to be u2 (unsigned 2-byte) values without other restrictions.
   */
  @Test
  public void testReference() throws Exception {
    testForJvm(parameters)
        .addProgramClassFileData(getClassWithZeroLineEntry())
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectOriginalStackTrace(st -> checkLineNumber(st, 0));
  }

  /**
   * Regression test for ASM stripping out zero-line entries.
   *
   * <p>See b/260389461
   */
  @Test
  public void testAsmIdentity() throws Exception {
    testForJvm(parameters)
        .addProgramClassFileData(
            getClassAfterAsmIdentity(
                getClassWithZeroLineEntry(), Reference.classFromClass(TestClass.class)))
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        // If this becomes zero ASM has been updated to not assign line zero a special meaning.
        .inspectOriginalStackTrace(st -> checkLineNumber(st, 1));
  }

  private void checkLineNumber(StackTrace st, int lineNumber) {
    assertThat(
        st,
        isSame(
            StackTrace.builder()
                .add(
                    StackTraceLine.builder()
                        .setFileName(getClass().getSimpleName() + ".java")
                        .setClassName(TestClass.class.getTypeName())
                        .setMethodName("main")
                        .setLineNumber(lineNumber)
                        .build())
                .build()));
  }

  private byte[] getClassAfterAsmIdentity(byte[] bytes, ClassReference clazz) {
    return transformer(bytes, clazz)
        // Add the identity line transform. If no method transformation is added then ASM will not
        // interpret the line table and will retain the zero.
        .addMethodTransformer(
            new MethodTransformer() {
              @Override
              public void visitLineNumber(int line, Label start) {
                super.visitLineNumber(line, start);
              }
            })
        .transform();
  }

  private byte[] getClassWithZeroLineEntry() throws IOException {
    IntBox nextLine = new IntBox(1);
    return transformer(TestClass.class)
        .addMethodTransformer(
            new MethodTransformer() {
              @Override
              public void visitLineNumber(int line, Label start) {
                if (getContext().getReference().getMethodName().equals("main")) {
                  int newLine = nextLine.get();
                  if (newLine > 0) {
                    nextLine.decrement(1);
                  }
                  super.visitLineNumber(newLine, start);
                } else {
                  super.visitLineNumber(line, start);
                }
              }
            })
        .transform();
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
      throw new RuntimeException("BOO!");
    }
  }
}
