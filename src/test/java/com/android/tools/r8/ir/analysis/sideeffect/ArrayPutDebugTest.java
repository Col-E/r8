// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.sideeffect;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArrayPutDebugTest extends TestBase {

  @Parameter(0)
  public boolean debug;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, debug: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .setMode(debug ? CompilationMode.DEBUG : CompilationMode.RELEASE)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(TestClass.class);
              assertThat(classSubject, isPresent());

              MethodSubject methodSubject = classSubject.mainMethod();
              assertThat(methodSubject, isPresent());
              assertEquals(
                  debug,
                  methodSubject.streamInstructions().anyMatch(InstructionSubject::isArrayPut));
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithEmptyOutput();
  }

  static class TestClass {

    public static void main(String[] args) {
      int[] xs = new int[1];
      xs[0] = 42; // Cannot be removed in debug mode because it has a side effect on the locals.
    }
  }
}
