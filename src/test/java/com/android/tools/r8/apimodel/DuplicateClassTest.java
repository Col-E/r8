// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
/**
 * A simple test showing that adding a program class as a duplicate for a bootclasspath class for
 * dalvik causes an error where the referencing class cannot be optimized (b/208978971).
 */
public class DuplicateClassTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(Main.class).removeInnerClasses().transform(),
            transformer(Foo.class)
                .setClassDescriptor("Ljava/lang/Exception;")
                .removeInnerClasses()
                .transform())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World")
        .applyIf(
            parameters.getDexRuntimeVersion().isDalvik(),
            result ->
                assertThat(
                    result.getStdErr(),
                    containsString(
                        "DexOpt: not resolving ambiguous class 'Ljava/lang/Exception;'")),
            result ->
                assertThat(
                    result.getStdErr(),
                    not(containsString("not resolving ambiguous class 'Ljava/lang/Exception;'"))));
  }

  public static class Main {

    public static void main(String[] args) {
      Exception exception = new Exception("Hello World");
      System.out.println(exception.getMessage());
    }
  }

  public static class /* java.lang.Exception */ Foo {}
}
