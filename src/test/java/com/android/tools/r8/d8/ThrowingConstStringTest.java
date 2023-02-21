// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.d8;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ThrowingConstStringTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello!");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public ThrowingConstStringTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Path cfout =
        testForD8(Backend.CF)
            .addInnerClasses(ThrowingConstStringTest.class)
            .setMinApi(parameters)
            .addOptionsModification(o -> o.testing.forceIRForCfToCfDesugar = true)
            .compile()
            .inspect(
                inspector -> {
                  MethodSubject method = inspector.clazz(TestClass.class).mainMethod();
                  InstructionSubject constString =
                      method.iterateInstructions(InstructionSubject::isConstString).next();
                  assertTrue(
                      "ConstString is not covered by try range",
                      method
                          .streamTryCatches()
                          .anyMatch(
                              tryCatch ->
                                  tryCatch.getRange().includes(constString.getOffset(method))));
                })
            .writeToZip();
    testForD8(parameters.getBackend())
        .addProgramFiles(cfout)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  static class TestClass {

    public static void main(String[] args) {
      synchronized (TestClass.class) {
        String constant = "Hello!";
        System.out.println(constant);
      }
    }
  }
}
