// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.cancelcompilationchecker;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CancelCompilationChecker;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.IntBox;
import java.util.function.BooleanSupplier;
import org.junit.Test;

public class CancelCompilationCheckerTest extends CompilerApiTestRunner {

  public CancelCompilationCheckerTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void testD8() throws Exception {
    // Use an integer box to delay the cancel until some time internal in the compiler.
    IntBox i = new IntBox();
    try {
      new ApiTest(ApiTest.PARAMETERS).runD8(() -> i.incrementAndGet() > 10);
      fail("excepted cancelled");
    } catch (CompilationFailedException e) {
      assertTrue(e.wasCancelled());
    }
  }

  @Test
  public void testR8() throws Exception {
    // Use an integer box to delay the cancel until some time internal in the compiler.
    IntBox i = new IntBox();
    try {
      new ApiTest(ApiTest.PARAMETERS).runR8(() -> i.incrementAndGet() > 20);
      fail("excepted cancelled");
    } catch (CompilationFailedException e) {
      assertTrue(e.wasCancelled());
    }
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void runD8(BooleanSupplier supplier) throws Exception {
      D8.run(
          D8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
              .setCancelCompilationChecker(
                  new CancelCompilationChecker() {
                    @Override
                    public boolean cancel() {
                      return supplier.getAsBoolean();
                    }
                  })
              .build());
    }

    public void runR8(BooleanSupplier supplier) throws Exception {
      R8.run(
          R8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setDisableTreeShaking(true)
              .setDisableMinification(true)
              .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
              .setCancelCompilationChecker(
                  new CancelCompilationChecker() {
                    @Override
                    public boolean cancel() {
                      return supplier.getAsBoolean();
                    }
                  })
              .build());
    }

    @Test
    public void testD8() throws Exception {
      try {
        runD8(() -> true);
      } catch (CompilationFailedException e) {
        if (e.wasCancelled()) {
          return;
        }
      }
      throw new AssertionError("expected cancelled");
    }

    @Test
    public void testR8() throws Exception {
      try {
        runR8(() -> true);
      } catch (CompilationFailedException e) {
        if (e.wasCancelled()) {
          return;
        }
      }
      throw new AssertionError("expected cancelled");
    }
  }
}
