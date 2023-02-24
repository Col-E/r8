// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.checkcast;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

class NullCheckCastTestMain {
  public static void main(String[] args) {
    System.out.println((SubClass) null);
  }
}

class Base {
}

class SubClass extends Base {
}

@RunWith(Parameterized.class)
public class NullCheckCastTest extends TestBase {

  private static final String EXPECTED_OUTPUT = "null";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(Base.class, SubClass.class, NullCheckCastTestMain.class)
        .run(parameters.getRuntime(), NullCheckCastTestMain.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramClasses(Base.class, SubClass.class, NullCheckCastTestMain.class)
            .addKeepMainRule(NullCheckCastTestMain.class)
            .setMinApi(parameters)
            .compile()
            .run(parameters.getRuntime(), NullCheckCastTestMain.class)
            .assertSuccessWithOutputLines(EXPECTED_OUTPUT)
            .inspector();

    ClassSubject mainSubject = inspector.clazz(NullCheckCastTestMain.class);
    assertThat(mainSubject, isPresent());
    MethodSubject mainMethod = mainSubject.mainMethod();
    assertThat(mainMethod, isPresent());

    // Check if the check-cast is gone.
    assertTrue(Streams.stream(mainMethod.iterateInstructions())
        .noneMatch(InstructionSubject::isCheckCast));

    // As check-cast is gone, other types can be discarded, too.
    ClassSubject classSubject = inspector.clazz(Base.class);
    assertThat(classSubject, not(isPresent()));
    classSubject = inspector.clazz(SubClass.class);
    assertThat(classSubject, not(isPresent()));
  }

}
