// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaReturnTypeBridgeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void testReturnTypeLambda()
      throws IOException, CompilationFailedException, ExecutionException {
    runTest(testForD8().addProgramClasses(LambdaWithMultipleImplementingInterfaces.class), false);
  }

  @Test
  public void testCovariantReturnTypeLambda()
      throws IOException, CompilationFailedException, ExecutionException {
    runTest(
        testForD8()
            .addProgramClassFileData(LambdaWithMultipleImplementingInterfacesCovariantDump.dump()),
        true);
  }

  private void runTest(D8TestBuilder builder, boolean shouldHaveCheckCast)
      throws IOException, CompilationFailedException, ExecutionException {
    builder
        .addInnerClasses(LambdaWithMultipleImplementingInterfaces.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), LambdaWithMultipleImplementingInterfaces.class)
        .assertSuccessWithOutputLines("Hello World!", "Hello World!")
        .inspect(
            codeInspector -> {
              boolean foundBridge = false;
              for (FoundClassSubject clazz : codeInspector.allClasses()) {
                if (clazz.isSynthesizedJavaLambdaClass()) {
                  // Find bridge method and check whether or not it has a cast.
                  for (FoundMethodSubject bridge : clazz.allMethods(FoundMethodSubject::isBridge)) {
                    foundBridge = true;
                    assertEquals(
                        shouldHaveCheckCast,
                        bridge.streamInstructions().anyMatch(InstructionSubject::isCheckCast));
                  }
                }
              }
              assertTrue(foundBridge);
            });
  }
}
