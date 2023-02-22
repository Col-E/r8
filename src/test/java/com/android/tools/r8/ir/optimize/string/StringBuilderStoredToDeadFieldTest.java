// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringBuilderStoredToDeadFieldTest extends TestBase {
  private static final Class<?> MAIN = TestClass.class;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(StringBuilderStoredToDeadFieldTest.class)
        .addKeepMainRule(MAIN)
        .addDontObfuscate()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithEmptyOutput()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject main = inspector.clazz(MAIN);
    assertThat(main, isPresent());

    MethodSubject mainMethod = main.mainMethod();
    assertThat(main, isPresent());

    assertTrue(
        mainMethod.streamInstructions().noneMatch(
            i -> i.isInvoke()
                && i.getMethod().holder.toDescriptorString().contains("StringBuilder")));
    assertTrue(
        mainMethod.streamInstructions().noneMatch(
            i -> i.isConstString(JumboStringMode.ALLOW)));
  }

  static class TestClass {
    static String neverRead;

    public static void main(String... args) {
      StringBuilder b = new StringBuilder();
      b.append("CurrentTimeMillis: ");
      b.append(System.currentTimeMillis());
      neverRead = b.toString();
    }
  }
}
