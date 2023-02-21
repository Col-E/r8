// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UnrelatedClasspathClassFieldAccessTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public UnrelatedClasspathClassFieldAccessTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public static class ClasspathClass {
    public static int field = 42;
  }

  public static class ProgramClass {

    public static void main(String[] args) {
      System.out.println(ClasspathClass.field);
    }
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(ProgramClass.class, ClasspathClass.class)
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutputLines("42");
  }

  @Test
  public void testApplyMapping() throws Exception {
    R8TestCompileResult classpath =
        testForR8(parameters.getBackend())
            .addProgramClasses(ClasspathClass.class)
            .addKeepAllClassesRuleWithAllowObfuscation()
            .setMinApi(parameters)
            .compile();

    testForR8(parameters.getBackend())
        .addProgramClasses(ProgramClass.class)
        .addClasspathClasses(ClasspathClass.class)
        .addApplyMapping(classpath.getProguardMap())
        .addKeepMainRule(ProgramClass.class)
        .addRunClasspathFiles(classpath.writeToZip())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutputLines("42");
  }
}
