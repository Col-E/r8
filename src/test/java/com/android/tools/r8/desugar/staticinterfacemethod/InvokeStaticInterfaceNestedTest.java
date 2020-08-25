// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.staticinterfacemethod;

import static com.android.tools.r8.desugar.staticinterfacemethod.InvokeStaticInterfaceNestedTest.Library.foo;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeStaticInterfaceNestedTest extends TestBase {

  private final TestParameters parameters;
  private final boolean cfToCfDesugar;
  private final String EXPECTED = "Hello World!";

  @Parameters(name = "{0}, desugar: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public InvokeStaticInterfaceNestedTest(TestParameters parameters, boolean cfToCfDesugar) {
    this.parameters = parameters;
    this.cfToCfDesugar = cfToCfDesugar;
  }

  @Test
  public void testRuntime() throws Exception {
    final SingleTestRunResult<?> runResult =
        testForRuntime(parameters)
            .addProgramClassFileData(
                rewriteToUseNonInterfaceMethodReference(Main.class, "main"),
                rewriteToUseNonInterfaceMethodReference(Library.class, "foo"))
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isCfRuntime() && parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK9)) {
      runResult.assertFailureWithErrorThatMatches(containsString("IncompatibleClassChangeError"));
    } else {
      // TODO(b/166247515): This should be ICCE.
      runResult.assertSuccessWithOutputLines(EXPECTED);
    }
  }

  @Test
  public void testR8() throws Exception {
    final R8FullTestBuilder testBuilder =
        testForR8(parameters.getBackend())
            .addProgramClassFileData(
                rewriteToUseNonInterfaceMethodReference(Main.class, "main"),
                rewriteToUseNonInterfaceMethodReference(Library.class, "foo"))
            .addKeepAllClassesRule()
            .setMinApi(parameters.getApiLevel())
            .addKeepMainRule(Main.class)
            .addOptionsModification(
                options -> {
                  options.cfToCfDesugar = cfToCfDesugar;
                });
    if (parameters.isCfRuntime()) {
      // TODO(b/166213037): Should not throw an error.
      assertThrows(CompilationFailedException.class, testBuilder::compile);
    } else {
      testBuilder.run(parameters.getRuntime(), Main.class).assertSuccessWithOutputLines(EXPECTED);
    }
  }

  private byte[] rewriteToUseNonInterfaceMethodReference(Class<?> clazz, String methodName)
      throws Exception {
    return transformer(clazz)
        .transformMethodInsnInMethod(
            methodName,
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              assertTrue(isInterface);
              visitor.visitMethodInsn(opcode, owner, name, descriptor, false);
            })
        .transform();
  }

  public interface Library {

    static void foo() {
      bar(); // <-- will be rewritten to invoke-static Library::bar();
    }

    static void bar() {
      System.out.println("Hello World!");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      foo(); // <-- will be rewritten to invoke-static Library::foo();
    }
  }
}
