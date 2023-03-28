// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CancelCompilationChecker;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.ThreadUtils.WorkLoad;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CancelFromIrConversionTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public CancelFromIrConversionTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws CompilationFailedException {
    assertTrue(
        "Ensure that we actually fork of threads",
        WorkLoad.LIGHT.getThreshold() <= getProgramClasses().size());
    BooleanBox cancel = new BooleanBox(false);
    try {
      testForD8()
          .addProgramClasses(getProgramClasses())
          .addOptionsModification(
              options ->
                  options.testing.hookInIrConversion =
                      () -> {
                        synchronized (cancel) {
                          cancel.set(true);
                        }
                      })
          .apply(
              b ->
                  b.getBuilder()
                      .setCancelCompilationChecker(
                          new CancelCompilationChecker() {
                            @Override
                            public boolean cancel() {
                              return cancel.get();
                            }
                          }))
          .compile();
      fail("Expected compilation to be cancelled.");
    } catch (CompilationFailedException e) {
      assertTrue(e.wasCancelled());
    }
  }

  private Collection<Class<?>> getProgramClasses() {
    return ImmutableList.of(A.class, B.class, C.class, D.class);
  }

  static class A {
    public void foo() {
      System.out.println("A::foo");
    }
  }

  static class B {
    public void bar() {
      System.out.println("B::bar");
    }
  }

  static class C {
    public void baz() {
      System.out.println("C::baz");
    }
  }

  static class D {
    public void foobar() {
      System.out.println("D::foobar");
    }
  }
}
