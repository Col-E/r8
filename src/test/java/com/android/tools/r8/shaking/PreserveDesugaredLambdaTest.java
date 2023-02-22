// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PreserveDesugaredLambdaTest extends TestBase {

  public interface Interface {
    void computeTheFoo();
  }

  public static class A {
    static void doFoo(Interface i) {
      i.computeTheFoo();
    }
  }

  public static class Main {
    public static void main(String[] args) {
      // The desugared lambda should be preserved.
      A.doFoo(() -> System.out.println("Hello World!"));
    }
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public PreserveDesugaredLambdaTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testKeepDesugaredLambdaMembers()
      throws IOException, CompilationFailedException, ExecutionException {
    R8TestCompileResult libraryCompileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(Interface.class, A.class)
            .addKeepAllClassesRule()
            .setMinApi(parameters)
            .compile();
    // A is not passed in to ensure the Enqueuer is not tracing through classpath to see the use of
    // computeFoo().
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addClasspathClasses(Interface.class)
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .addKeepAllClassesRule()
        .addDontWarn(A.class)
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(libraryCompileResult.writeToZip())
        .inspect(
            codeInspector ->
                assertTrue(
                    codeInspector.allClasses().stream()
                        .anyMatch(
                            c -> {
                              if (c.isSynthesizedJavaLambdaClass()) {
                                assertThat(
                                    c.uniqueMethodWithOriginalName("computeTheFoo"), isPresent());
                                return true;
                              }
                              return false;
                            })))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }
}
