// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dexsplitter;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8FeatureSplitTest extends TestBase {

  private static String EXPECTED = "Hello world";

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withDexRuntimes().build();
  }

  private final TestParameters parameters;

  public R8FeatureSplitTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static FeatureSplit emptySplitProvider(FeatureSplit.Builder builder) {
    builder
        .addProgramResourceProvider(
            new ProgramResourceProvider() {
              @Override
              public Collection<ProgramResource> getProgramResources() throws ResourceException {
                return null;
              }
            })
        .setProgramConsumer(
            new DexIndexedConsumer() {
              @Override
              public void finished(DiagnosticsHandler handler) {}
            });
    return builder.build();
  }

  @Test
  public void simpleApiTest() throws CompilationFailedException, IOException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(HelloWorld.class)
        .setMinApi(parameters.getRuntime())
        .addFeatureSplit(R8FeatureSplitTest::emptySplitProvider)
        .addKeepMainRule(HelloWorld.class)
        .compile()
        .run(parameters.getRuntime(), HelloWorld.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class HelloWorld {
    public static void main(String[] args) {
      System.out.println("Hello world");
    }
  }
}
