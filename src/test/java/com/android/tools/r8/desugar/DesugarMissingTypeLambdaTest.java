// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.DesugarDiagnostic;
import com.android.tools.r8.errors.InterfaceDesugarMissingTypeDiagnostic;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugarMissingTypeLambdaTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public DesugarMissingTypeLambdaTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  boolean supportsDefaultInterfaceMethods() {
    return parameters.getRuntime().isCf()
        || AndroidApiLevel.N.getLevel() <= parameters.getApiLevel().getLevel();
  }

  @Test
  public void test() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addProgramClasses(TestClass.class, MissingInterface.class)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED);
    } else {
      D8TestBuilder builder = testForD8().addProgramClasses(TestClass.class).setMinApi(parameters);
      TestDiagnosticMessages messages = builder.getState().getDiagnosticsMessages();
      D8TestCompileResult compileResult = builder.compile();
      if (supportsDefaultInterfaceMethods()) {
        messages.assertNoMessages();
        compileResult
            .addRunClasspathFiles(
                testForD8()
                    .setMinApi(parameters)
                    .addProgramClasses(MissingInterface.class)
                    .compile()
                    .writeToZip())
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(EXPECTED);
      } else {
        messages.assertOnlyWarnings();
        assertEquals(1, messages.getWarnings().size());
        Diagnostic diagnostic = messages.getWarnings().get(0);
        assertTrue(diagnostic instanceof DesugarDiagnostic);
        assertTrue(diagnostic instanceof InterfaceDesugarMissingTypeDiagnostic);
        InterfaceDesugarMissingTypeDiagnostic desugarWarning = (InterfaceDesugarMissingTypeDiagnostic) diagnostic;
        assertEquals(
            Reference.classFromClass(MissingInterface.class), desugarWarning.getMissingType());
        // TODO(b/132671303): The context class should not be the synthesized lambda class.
        assertTrue(SyntheticItemsTestUtils.isInternalLambda(desugarWarning.getContextType()));
        // TODO(b/132671303): The position info should be the method context.
        assertEquals(Position.UNKNOWN, desugarWarning.getPosition());
      }
    }
  }

  public interface MissingInterface {
    void foo();

    default void bar() {
      foo();
    }
  }

  static class TestClass {

    public static void baz(MissingInterface fn) {
      fn.bar();
    }

    public static void main(String[] args) {
      baz(() -> System.out.println("Hello, world"));
    }
  }
}
