// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;


import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.codeinspector.AssertUtils;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MissingClassThrowingTest extends TestBase {

  @NoHorizontalClassMerging
  public static class MissingException extends Exception {}

  @NoHorizontalClassMerging
  public static class Program {

    public static final String EXPECTED_OUTPUT = "Hello world!";

    @NeverInline
    private static char[] compile(String[] args) throws IOException, MissingException {
      if (args.length > 1) {
        throw new MissingException();
      } else if (args.length == 1) {
        throw new IOException();
      } else {
        return EXPECTED_OUTPUT.toCharArray();
      }
    }

    @NeverInline
    public static void main(String[] args) {
      try {
        System.out.println(compile(args));
      } catch (IOException | MissingException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntime(CfVm.last()).build();
  }

  public MissingClassThrowingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testSuperTypeOfExceptions() throws Exception {
    AssertUtils.assertFailsCompilation(
        () ->
            testForR8(parameters.getBackend())
                .addProgramClasses(Program.class)
                .addKeepAllClassesRule()
                .addKeepAllAttributes()
                .addDontObfuscate()
                .noTreeShaking()
                .enableInliningAnnotations()
                .enableNoHorizontalClassMergingAnnotations()
                .debug()
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics
                            .assertOnlyErrors()
                            .inspectErrors(
                                diagnostic ->
                                    diagnostic
                                        .assertIsMissingDefinitionsDiagnostic()
                                        .assertIsMissingClass(MissingException.class)
                                        .assertNumberOfMissingClasses(1))));
  }
}
