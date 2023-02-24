// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClasspathAndProgramSharedSuperTypeTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() {
    // TODO(b/226054007): R8 should maintain classpath type.
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addProgramClasses(SharedSuperType.class, ProgramClass.class, Main.class)
                .addClasspathClasses(SharedSuperType.class, ClasspathClass.class)
                .setMinApi(parameters)
                .addKeepMainRule(Main.class)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorMessageThatMatches(
                            containsString(
                                "Failed lookup of non-missing type: "
                                    + typeName(SharedSuperType.class)))));
  }

  public static class SharedSuperType {

    public void foo() {
      System.out.println("SharedSuperType::foo");
    }
  }

  public static class ClasspathClass extends SharedSuperType {

    @Override
    public void foo() {
      System.out.println("ClasspathClass::foo");
      super.foo();
    }
  }

  /* Not referenced in live part of the program */
  public static class ProgramClass extends SharedSuperType {

    @Override
    public void foo() {
      System.out.println("ProgramClass::foo");
      super.foo();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new ClasspathClass().foo();
    }
  }
}
