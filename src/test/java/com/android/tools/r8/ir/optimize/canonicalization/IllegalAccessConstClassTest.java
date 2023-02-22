// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.canonicalization;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Will be changed to different package.
class PackagePrivateClass {
}

// Will be changed to use the above class in a different package.
class FakePackagePrivateClassConsumer {
  public static void main(String... args) {
    if (System.currentTimeMillis() < -2) {
      System.out.println(PackagePrivateClass.class.getName());
    } else if (System.currentTimeMillis() < -1) {
      System.out.println(PackagePrivateClass.class.getSimpleName());
    } else {
      System.out.println("No need to load any classes");
    }
  }
}

@RunWith(Parameterized.class)
public class IllegalAccessConstClassTest extends TestBase {
  private static final Class<?> MAIN = FakePackagePrivateClassConsumer.class;
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "No need to load any classes"
  );

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testJVMOutput() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .release()
        .addProgramClassFileData(IllegalAccessConstClassTestDump.PackagePrivateClassDump.dump())
        .addProgramClassFileData(
            IllegalAccessConstClassTestDump.FakePackagePrivateClassConsumerDump.dump())
        .addOptionsModification(InternalOptions::disableNameReflectionOptimization)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(IllegalAccessConstClassTestDump.PackagePrivateClassDump.dump())
        .addProgramClassFileData(
            IllegalAccessConstClassTestDump.FakePackagePrivateClassConsumerDump.dump())
        .addKeepMainRule(MAIN)
        .addDontObfuscate()
        .addOptionsModification(InternalOptions::disableNameReflectionOptimization)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject prvClass = inspector.clazz("PackagePrivateClass");
    assertThat(prvClass, isPresent());

    ClassSubject mainClass = inspector.clazz(MAIN);
    assertThat(mainClass, isPresent());
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    // No canonicalization of const-class instructions.
    assertEquals(
        2,
        mainMethod.streamInstructions().filter(InstructionSubject::isConstClass).count());
  }
}
