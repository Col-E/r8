// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.desugardependencies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DesugarGraphConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.origin.Origin;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Test;

public class DesugarDependenciesTest extends CompilerApiTestRunner {

  public DesugarDependenciesTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void testDesugarDependencies() throws Exception {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    runTest(test::run);
  }

  private interface Runner {
    void run() throws Exception;
  }

  private void runTest(Runner test) throws Exception {
    test.run();
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void run() throws Exception {
      D8.run(
          D8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
              .setDesugarGraphConsumer(
                  new DesugarGraphConsumer() {
                    private final Set<Origin> desugaringUnit = ConcurrentHashMap.newKeySet();

                    @Override
                    public void acceptProgramNode(Origin node) {
                      desugaringUnit.add(node);
                    }

                    @Override
                    public void accept(Origin dependent, Origin dependency) {
                      assertTrue(desugaringUnit.contains(dependent));
                    }

                    @Override
                    public void finished() {
                      // Input unit contains just the mock class.
                      assertEquals(1, desugaringUnit.size());
                    }
                  })
              .build());
    }

    @Test
    public void testDesugarDependencies() throws Exception {
      run();
    }
  }
}
