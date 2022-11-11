// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.ClassAccessFlags;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NewInstanceToAbstractClassReferenceTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .addProgramClassFileData(
            transformer(A.class).setAccessFlags(ClassAccessFlags::setAbstract).transform())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrowsIf(parameters.isCfRuntime(), InstantiationError.class)
        .applyIf(
            parameters.isDexRuntime(),
            result -> {
              if (parameters.getDexRuntimeVersion().isDalvik()) {
                result.assertStderrMatches(
                    containsString(
                        "VFY: new-instance on interface or abstract class " + descriptor(A.class)));

              } else if (parameters.getDexRuntimeVersion().isOlderThan(Version.V7_0_0)) {
                result.assertStderrMatches(
                    containsString(
                        "Verification failed on class "
                            + typeName(NewInstanceToAbstractClassReferenceTest.class)));
              } else {
                result.assertStderrMatches(not(containsString("Verification failed")));
              }
            });
  }

  public static class A {

    public void foo() {
      System.out.println("A::foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new A().foo();
    }
  }
}
